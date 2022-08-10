package com.bitwig.extensions.controllers.m_audio;

import java.util.UUID;

public class OxygenPro25Definition extends OxygenProDefinition {

    private static final UUID DRIVER_ID = UUID.fromString("6e0c935f-bd37-4bba-8584-e368aa92444f");

    @Override
    String getModel() {
        // TODO Auto-generated method stub
        return "25";
    }

    @Override
    public UUID getId() {
        // TODO Auto-generated method stub
        return DRIVER_ID;
    }

    
}
