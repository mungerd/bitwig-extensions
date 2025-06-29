package com.bitwig.extensions.controllers.arturia.minilab3;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.values.Midi;

public class KompleteKontrolLayer extends Layer {
   private final MiniLab3Extension driver;


   public KompleteKontrolLayer(final MiniLab3Extension driver) {
      super(driver.getLayers(), "KOMPLETE_KONTROL");
      this.driver = driver;
   }

    public void notifyMidi(final ShortMidiMessage msg) {
        if (!isActive()) {
            return;
        }
        driver.getCursorTrack().sendMidi(msg.getStatusByte(), msg.getData1(), msg.getData2());
    }
    
}
