package com.tonyleiva.parkinggarage.application.garage;

public enum GarageStartupOrigin {
  SIMULATOR("SIMULADOR"),
  DATABASE("BANCO_DE_DADOS");

  private final String logValue;

  GarageStartupOrigin(String logValue) { this.logValue = logValue; }

  public String logValue() { return logValue; }
}
