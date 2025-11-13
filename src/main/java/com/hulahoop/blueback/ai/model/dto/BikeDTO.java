package com.hulahoop.blueback.ai.model.dto;

public class BikeDTO {
    private String bicycleCode;
    private String bicycleType;
    private String status;
    private double latitude;
    private double longitude;

    public BikeDTO() {
    }

    public BikeDTO(String bicycleCode, String bicycleType, String status, double latitude, double longitude) {
        this.bicycleCode = bicycleCode;
        this.bicycleType = bicycleType;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getBicycleCode() {
        return bicycleCode;
    }

    public void setBicycleCode(String bicycleCode) {
        this.bicycleCode = bicycleCode;
    }

    public String getBicycleType() {
        return bicycleType;
    }

    public void setBicycleType(String bicycleType) {
        this.bicycleType = bicycleType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}
