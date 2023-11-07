/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mysql.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.kafka.connect.connector.Connector;

import io.debezium.connector.mysql.Module;
import io.debezium.connector.mysql.MySqlConnector;
import io.debezium.rest.ConnectionValidationResource;
import io.debezium.rest.SchemaResource;

/**
 * A JAX-RS Resource class defining endpoints of the Debezium MySQL Connect REST Extension
 *
 */
@Path(DebeziumMySqlConnectorResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DebeziumMySqlConnectorResource implements SchemaResource, ConnectionValidationResource {

    public static final String BASE_PATH = "/debezium/mysql";
    public static final String VERSION_ENDPOINT = "/version";
    public static final String VALIDATE_PROPERTIES_ENDPOINT = "/validate/properties";
    public static final String VALIDATE_CONNECTOR_ENDPOINT = "/validate/connector";

    @Override
    public String getSchemaFilePath() {
        return "/META-INF/resources/mysql.json";
    }

    @Override
    public Connector getConnector() {
        return new MySqlConnector();
    }

    @GET
    @Path(VERSION_ENDPOINT)
    public String getConnectorVersion() {
        return Module.version();
    }

}
