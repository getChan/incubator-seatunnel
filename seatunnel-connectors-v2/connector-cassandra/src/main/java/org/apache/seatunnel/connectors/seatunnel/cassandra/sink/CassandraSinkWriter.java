/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.cassandra.sink;

import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.utils.ExceptionUtils;
import org.apache.seatunnel.connectors.seatunnel.cassandra.client.CassandraClient;
import org.apache.seatunnel.connectors.seatunnel.cassandra.config.CassandraConfig;
import org.apache.seatunnel.connectors.seatunnel.cassandra.util.TypeConvertUtil;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSinkWriter;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.DataType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CassandraSinkWriter extends AbstractSinkWriter<SeaTunnelRow, Void> {

    private final CassandraConfig cassandraConfig;
    private final SeaTunnelRowType seaTunnelRowType;
    private final ColumnDefinitions tableSchema;
    private final CqlSession session;
    private BatchStatement batchStatement;
    private List<BoundStatement> boundStatementList;
    private List<CompletionStage<AsyncResultSet>> completionStages;
    private final PreparedStatement preparedStatement;
    private final AtomicInteger counter = new AtomicInteger(0);

    public CassandraSinkWriter(CassandraConfig cassandraConfig, SeaTunnelRowType seaTunnelRowType, ColumnDefinitions tableSchema) {
        this.cassandraConfig = cassandraConfig;
        this.seaTunnelRowType = seaTunnelRowType;
        this.tableSchema = tableSchema;
        this.session = CassandraClient.getCqlSessionBuilder(
            cassandraConfig.getHost(),
            cassandraConfig.getKeyspace(),
            cassandraConfig.getUsername(),
            cassandraConfig.getPassword(),
            cassandraConfig.getDatacenter()).build();
        this.batchStatement = BatchStatement.builder(cassandraConfig.getBatchType()).build();
        this.boundStatementList = new ArrayList<>();
        this.completionStages = new ArrayList<>();
        this.preparedStatement = session.prepare(initPrepareCQL());
    }

    @Override
    public void write(SeaTunnelRow row) throws IOException {
        BoundStatement boundStatement = this.preparedStatement.bind();
        addIntoBatch(row, boundStatement);
        if (counter.getAndIncrement() >= cassandraConfig.getBatchSize()) {
            flush();
            counter.set(0);
        }
    }

    private void flush() {
        if (cassandraConfig.getAsyncWrite()) {
            completionStages.forEach(resultStage -> resultStage.whenComplete(
                (resultSet, error) -> {
                    if (error != null) {
                        log.error(ExceptionUtils.getMessage(error));
                    }
                }
            ));
            completionStages.clear();
        } else {
            try {
                this.session.execute(this.batchStatement.addAll(boundStatementList));
            } catch (Exception e) {
                log.error("Batch insert error,Try inserting one by one!");
                for (BoundStatement statement : boundStatementList) {
                    this.session.execute(statement);
                }
            } finally {
                this.batchStatement.clear();
                this.boundStatementList.clear();
            }
        }

    }

    private void addIntoBatch(SeaTunnelRow row, BoundStatement boundStatement) {
        try {
            for (int i = 0; i < cassandraConfig.getFields().size(); i++) {
                String fieldName = cassandraConfig.getFields().get(i);
                DataType dataType = tableSchema.get(i).getType();
                Object fieldValue = row.getField(seaTunnelRowType.indexOf(fieldName));
                boundStatement = TypeConvertUtil.reconvertAndInject(boundStatement, i, dataType, fieldValue);
            }
            if (cassandraConfig.getAsyncWrite()) {
                completionStages.add(session.executeAsync(boundStatement));
            } else {
                boundStatementList.add(boundStatement);
            }
        } catch (Exception e) {
            throw new RuntimeException("Add row data into batch error!", e);
        }
    }

    private String initPrepareCQL() {
        String[] placeholder = new String[cassandraConfig.getFields().size()];
        Arrays.fill(placeholder, "?");
        return String.format("INSERT INTO %s (%s) VALUES (%s)",
            cassandraConfig.getTable(),
            String.join(",", cassandraConfig.getFields()),
            String.join(",", placeholder));
    }

    @Override
    public void close() throws IOException {
        flush();
        try {
            if (this.session != null) {
                this.session.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to close CqlSession!", e);
        }

    }
}
