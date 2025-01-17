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

package org.apache.seatunnel.connectors.seatunnel.sink;

import static org.apache.seatunnel.connectors.seatunnel.config.DataHubConfig.ACCESS_ID;
import static org.apache.seatunnel.connectors.seatunnel.config.DataHubConfig.ACCESS_KEY;
import static org.apache.seatunnel.connectors.seatunnel.config.DataHubConfig.ENDPOINT;
import static org.apache.seatunnel.connectors.seatunnel.config.DataHubConfig.PROJECT;
import static org.apache.seatunnel.connectors.seatunnel.config.DataHubConfig.RETRY_TIMES;
import static org.apache.seatunnel.connectors.seatunnel.config.DataHubConfig.TIMEOUT;
import static org.apache.seatunnel.connectors.seatunnel.config.DataHubConfig.TOPIC;

import org.apache.seatunnel.api.common.PrepareFailException;
import org.apache.seatunnel.api.sink.SeaTunnelSink;
import org.apache.seatunnel.api.sink.SinkWriter.Context;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.config.CheckConfigUtil;
import org.apache.seatunnel.common.config.CheckResult;
import org.apache.seatunnel.common.constants.PluginType;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSimpleSink;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSinkWriter;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import com.google.auto.service.AutoService;

import java.io.IOException;


/**
 * DataHub sink class
 */
@AutoService(SeaTunnelSink.class)
public class DataHubSink extends AbstractSimpleSink<SeaTunnelRow, Void> {

    private Config pluginConfig;
    private SeaTunnelRowType seaTunnelRowType;

    @Override
    public String getPluginName() {
        return "DataHub";
    }

    @Override
    public void prepare(Config pluginConfig) throws PrepareFailException {
        CheckResult result = CheckConfigUtil.checkAllExists(pluginConfig,
            ENDPOINT.key(), ACCESS_ID.key(), ACCESS_KEY.key(), PROJECT.key(), TOPIC.key());
        if (!result.isSuccess()) {
            throw new PrepareFailException(getPluginName(), PluginType.SINK, result.getMsg());
        }
        this.pluginConfig = pluginConfig;
    }

    @Override
    public void setTypeInfo(SeaTunnelRowType seaTunnelRowType) {
        this.seaTunnelRowType = seaTunnelRowType;
    }

    @Override
    public SeaTunnelDataType<SeaTunnelRow> getConsumedType() {
        return this.seaTunnelRowType;
    }

    @Override
    public AbstractSinkWriter<SeaTunnelRow, Void> createWriter(Context context) throws IOException {
        return new DataHubWriter(seaTunnelRowType,
            pluginConfig.getString(ENDPOINT.key()),
            pluginConfig.getString(ACCESS_ID.key()),
            pluginConfig.getString(ACCESS_KEY.key()),
            pluginConfig.getString(PROJECT.key()),
            pluginConfig.getString(TOPIC.key()),
            pluginConfig.getInt(TIMEOUT.key()),
            pluginConfig.getInt(RETRY_TIMES.key()));
    }
}
