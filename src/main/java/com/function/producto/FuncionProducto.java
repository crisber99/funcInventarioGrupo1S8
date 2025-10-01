package com.function.producto;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class FuncionProducto {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @FunctionName("Productos")
    public HttpResponseMessage handleBaseRequests(
            @HttpTrigger(name = "req", methods = { HttpMethod.GET,
                    HttpMethod.POST }, route = "productos", authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        activaEvento(request, context);

        String dbConnectionString = System.getenv("DB_CONNECTION_STRING");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");

        Properties connectionProps = new Properties();
        connectionProps.put("user", dbUser);
        connectionProps.put("password", dbPassword);

        try (Connection conn = DriverManager.getConnection(dbConnectionString, connectionProps)) {

            if (request.getHttpMethod() == HttpMethod.GET) {
                // READ (Obtener todos los productos)
                return readAllProductos(conn, request);
            } else if (request.getHttpMethod() == HttpMethod.POST) {
                // CREATE
                Producto newProducto = objectMapper.readValue(request.getBody().get(), Producto.class);
                return createProducto(conn, newProducto, request);
            }

        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json").body("Error: " + e.getMessage())
                    .build();
        }
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("Método no soportado en esta ruta.").build();
    }

    public HttpResponseMessage activaEvento(HttpRequestMessage<Optional<String>> request, ExecutionContext context) {
        String eventGridTopicEndpoint = "https://event-grupo1.eastus2-1.eventgrid.azure.net/api/events";
        String eventGridTopicKey = System.getenv("EVENT");;

        try {
            EventGridPublisherClient<EventGridEvent> client = new EventGridPublisherClientBuilder()
                    .endpoint(eventGridTopicEndpoint)
                    .credential(new AzureKeyCredential(eventGridTopicKey))
                    .buildEventGridEventPublisherClient();

            EventGridEvent event = new EventGridEvent("/EventGridEvents/example/source",
                    "Example.EventType", BinaryData.fromObject("Hello world"), "0.1");

            client.sendEvent(event);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body("Evento creado correctamente")
                    .build();

        } catch (Exception ex) {
            context.getLogger().severe("error al publicar evento: " + ex.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("error al publicar evento: " + ex.getMessage())
                    .build();
        }
    }

    @FunctionName("ProductosById")
    public HttpResponseMessage handleIdRequests(
            @HttpTrigger(name = "req", methods = { HttpMethod.GET, HttpMethod.PUT,
                    HttpMethod.DELETE }, route = "productos/{id}", authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext context) {

        String dbConnectionString = System.getenv("DB_CONNECTION_STRING");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");

        Properties connectionProps = new Properties();
        connectionProps.put("user", dbUser);
        connectionProps.put("password", dbPassword);

        try (Connection conn = DriverManager.getConnection(dbConnectionString, connectionProps)) {

            if (request.getHttpMethod() == HttpMethod.GET) {
                // READ (Obtener un producto por ID)
                return readProductoById(conn, Integer.parseInt(id), request);
            } else if (request.getHttpMethod() == HttpMethod.PUT) {
                // UPDATE
                Producto updatedProducto = objectMapper.readValue(request.getBody().get(), Producto.class);
                return updateProducto(conn, Integer.parseInt(id), updatedProducto, request);
            } else if (request.getHttpMethod() == HttpMethod.DELETE) {
                // DELETE
                return deleteProducto(conn, Integer.parseInt(id), request);
            }

        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("Error: " + e.getMessage())
                    .build();
        }
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("Método no soportado en esta ruta.").build();
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

    private HttpResponseMessage createProducto(Connection conn, Producto newProducto,
            HttpRequestMessage<Optional<String>> request) throws SQLException {
        String query = "INSERT INTO PRODUCTOS (NOMBRE, DESCRIPCION, PRECIO, STOCK, ID_BODEGA) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, newProducto.getNombre());
            stmt.setString(2, newProducto.getDescripcion());
            stmt.setDouble(3, newProducto.getPrecio());
            stmt.setInt(4, newProducto.getStock());
            stmt.setInt(5, newProducto.getIdBodega());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    newProducto.setId(generatedKeys.getInt(1));
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

    private HttpResponseMessage updateProducto(Connection conn, int id, Producto updatedProducto,
            HttpRequestMessage<Optional<String>> request) throws SQLException {
        String query = "UPDATE PRODUCTOS SET NOMBRE=?, DESCRIPCION=?, PRECIO=?, STOCK=?, ID_BODEGA=? WHERE ID=?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, updatedProducto.getNombre());
            stmt.setString(2, updatedProducto.getDescripcion());
            stmt.setDouble(3, updatedProducto.getPrecio());
            stmt.setInt(4, updatedProducto.getStock());
            stmt.setInt(5, updatedProducto.getIdBodega());
            stmt.setInt(6, id);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(updatedProducto).build();
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
