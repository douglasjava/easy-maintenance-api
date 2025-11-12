package com.brainbyte.easy_maintenance.assets.domain.enums;

public enum ItemCategory {

  REGULATORY,
  OPERATIONAL;

  public boolean isOperational() {
    return this == OPERATIONAL;
  }

  public boolean isRegulatory() {
    return this == REGULATORY;
  }

}
