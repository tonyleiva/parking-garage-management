package com.tonyleiva.parkinggarage.application.revenue;

public class SectorNotFoundException extends RuntimeException {
  public SectorNotFoundException(String sectorCode) {
    super("O setor informado não foi encontrado: " + sectorCode + ".");
  }
}
