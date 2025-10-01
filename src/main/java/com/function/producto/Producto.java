package com.function.producto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Producto {
    @JsonProperty("id")
    private int id;
    @JsonProperty("nombre")
    private String nombre;
    @JsonProperty("descripcion")
    private String descripcion;
    @JsonProperty("precio")
    private double precio;
    @JsonProperty("stock")
    private int stock;
    @JsonProperty("id_bodega")
    private int id_bodega;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public double getPrecio() {
        return precio;
    }

    public void setPrecio(double precio) {
        this.precio = precio;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public int getIdBodega() {
        return id_bodega;
    }

    public void setIdBodega(int id_bodega) {
        this.id_bodega = id_bodega;
    }
}