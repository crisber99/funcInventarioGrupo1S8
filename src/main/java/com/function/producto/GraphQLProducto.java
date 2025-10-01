package com.function.producto;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

public class GraphQLProducto {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FunctionName("GraphQLProductos")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.POST }, route = "graphqlProducto", authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        String dbConnectionString = System.getenv("DB_CONNECTION_STRING");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");

        Properties connectionProps = new Properties();
        connectionProps.put("user", dbUser);
        connectionProps.put("password", dbPassword);

        try (Connection conn = DriverManager.getConnection(dbConnectionString, connectionProps)) {

            Map<String, Object> requestBody = objectMapper.readValue(
                    request.getBody().get(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });

            String query = (String) requestBody.get("query");
            Map<String, Object> variables = (Map<String, Object>) requestBody.get("variables");

            if (query != null && query.contains("mutation")) {
                if (query.contains("createProducto")) {
                    if (variables != null && variables.containsKey("producto")) {
                        Map<String, Object> bodegaData = (Map<String, Object>) variables.get("producto");
                        return createProducto(conn, bodegaData, request);
                    } else {
                        throw new IllegalArgumentException(
                                "El objeto 'producto' no fue proporcionado en las variables.");
                    }
                } else if (query.contains("updateProducto")) {
                    if (variables != null && variables.containsKey("id") && variables.containsKey("producto")) {
                        int id = ((Number) variables.get("id")).intValue();
                        Map<String, Object> bodegaData = (Map<String, Object>) variables.get("producto");
                        return updateProducto(conn, id, bodegaData, request);
                    } else {
                        throw new IllegalArgumentException("El ID o el objeto 'producto' no fue proporcionado.");
                    }
                } else if (query.contains("deleteProducto")) {
                    int id = ((Number) variables.get("id")).intValue();
                    return deleteProducto(conn, id, request);
                }
            } else if (query != null && query.contains("query")) {
                if (variables != null && variables.containsKey("id")) {
                    int id = ((Number) variables.get("id")).intValue();
                    return readProductoById(conn, id, request);
                } else {
                    return readAllProductos(conn, request);
                }
            }
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("Error en la operación GraphQL: " + e.getMessage()).build();
        }
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("Operación GraphQL no soportada.").build();
    }

    private HttpResponseMessage readAllProductos(Connection conn, HttpRequestMessage<Optional<String>> request)
            throws SQLException {
        List<Producto> productos = new ArrayList<>();
        String query = "SELECT ID, NOMBRE, DESCRIPCION, PRECIO, STOCK, ID_BODEGA FROM PRODUCTOS";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                Producto producto = new Producto();
                producto.setId(rs.getInt("ID"));
                producto.setNombre(rs.getString("NOMBRE"));
                producto.setDescripcion(rs.getString("DESCRIPCION"));
                producto.setPrecio(rs.getDouble("PRECIO"));
                producto.setStock(rs.getInt("STOCK"));
                producto.setIdBodega(rs.getInt("ID_BODEGA"));
                productos.add(producto);
            }
        }
        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(productos).build();
    }

    private HttpResponseMessage readProductoById(Connection conn, int id, HttpRequestMessage<Optional<String>> request)
            throws SQLException {
        String query = "SELECT ID, NOMBRE, DESCRIPCION, PRECIO, STOCK, ID_BODEGA FROM PRODUCTOS WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Producto producto = new Producto();
                producto.setId(rs.getInt("ID"));
                producto.setNombre(rs.getString("NOMBRE"));
                producto.setDescripcion(rs.getString("DESCRIPCION"));
                producto.setPrecio(rs.getDouble("PRECIO"));
                producto.setStock(rs.getInt("STOCK"));
                producto.setIdBodega(rs.getInt("ID_BODEGA"));
                return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(producto).build();
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "application/json")
                        .body("Producto no encontrado.").build();
            }
        }
    }

    private HttpResponseMessage createProducto(Connection conn, Map<String, Object> productoData,
            HttpRequestMessage<Optional<String>> request) throws SQLException {
        String query = "INSERT INTO PRODUCTOS (NOMBRE, DESCRIPCION, PRECIO, STOCK, ID_BODEGA) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, (String) productoData.get("nombre"));
            stmt.setString(2, (String) productoData.get("descripcion"));
            stmt.setDouble(3, ((Number) productoData.get("precio")).doubleValue());
            stmt.setInt(4, ((Number) productoData.get("stock")).intValue());
            stmt.setInt(5, ((Number) productoData.get("idBodega")).intValue());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    Producto newProducto = new Producto();
                    newProducto.setId(generatedKeys.getInt(1));
                    newProducto.setNombre((String) productoData.get("nombre"));
                    newProducto.setDescripcion((String) productoData.get("descripcion"));
                    newProducto.setPrecio((double) productoData.get("precio"));
                    newProducto.setStock((int) productoData.get("stock"));
                    newProducto.setIdBodega((int) productoData.get("idBodega"));
                    return request.createResponseBuilder(HttpStatus.CREATED)
                            .header("Content-Type", "application/json")
                            .body(newProducto).build();
                }
            }
        }
        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body("Error al crear el producto.")
                .build();
    }

    private HttpResponseMessage updateProducto(Connection conn, int id, Map<String, Object> productoData,
            HttpRequestMessage<Optional<String>> request) throws SQLException {
        String query = "UPDATE PRODUCTOS SET NOMBRE=?, DESCRIPCION=?, PRECIO=?, STOCK=?, ID_BODEGA=? WHERE ID=?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, (String) productoData.get("nombre"));
            stmt.setString(2, (String) productoData.get("descripcion"));
            stmt.setDouble(3, ((Number) productoData.get("precio")).doubleValue());
            stmt.setInt(4, ((Number) productoData.get("stock")).intValue());
            stmt.setInt(5, ((Number) productoData.get("idBodega")).intValue());
            stmt.setInt(6, id);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(productoData).build();
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "application/json")
                        .body("Producto no encontrado.").build();
            }
        }
    }

    private HttpResponseMessage deleteProducto(Connection conn, int id, HttpRequestMessage<Optional<String>> request)
            throws SQLException {
        String query = "DELETE FROM PRODUCTOS WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                return request.createResponseBuilder(HttpStatus.NO_CONTENT)
                        .header("Content-Type", "application/json")
                        .build();
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "application/json")
                        .body("Producto no encontrado.").build();
            }
        }
    }
}
