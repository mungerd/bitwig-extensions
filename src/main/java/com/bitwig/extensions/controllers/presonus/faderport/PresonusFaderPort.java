package com.bitwig.extensions.controllers.presonus.faderport;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.callback.DoubleValueChangedCallback;
import com.bitwig.extension.callback.ShortMidiMessageReceivedCallback;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.Action;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.Arranger;
import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CueMarkerBank;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.HardwareButton;
import com.bitwig.extension.controller.api.HardwareSlider;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.MasterTrack;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.MultiStateHardwareLight;
import com.bitwig.extension.controller.api.OnOffHardwareLight;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.RelativeHardwareKnob;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.SettableIntegerValue;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extensions.framework.LayerGroup;
import com.bitwig.extensions.framework.Layers;
import com.bitwig.extensions.util.ValueUtils;

public class PresonusFaderPort extends ControllerExtension
{
   private static final Color WHITE = Color.fromRGB(1.f, 1.f, 1.f);

   private static final Color DIM_WHITE = Color.fromRGB(0.1f, 0.1f, 0.1f);

   private static final Color HALF_WHITE = Color.fromRGB(0.3f, 0.3f, 0.3f);

   private static final Color BLACK = Color.fromRGB(0.f, 0.f, 0.f);

   private static final Color ARM_LOW = Color.fromRGB(0.1f, 0.0f, 0.0f);

   private static final Color ARM_HIGH = Color.fromRGB(1.0f, 0.0f, 0.0f);

   static int[] SELECT_IDS = { 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x7, 0x21, 0x22, 0x23, 0x24,
         0x25, 0x26, 0x27 };

   static int[] SOLOD_IDS = { 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x50, 0x51, 0x52, 0x58, 0x54,
         0x55, 0x59, 0x57 };

   private RelativeHardwareKnob mDisplayEncoder;

   private HardwareButton mMasterButton;

   private HardwareButton mShiftLeftButton;

   private HardwareButton mShiftRightButton;

   private HardwareButton mPlayButton;

   private HardwareButton mStopButton;

   private HardwareButton mRecordButton;

   private HardwareButton mMetronomeButton;

   private HardwareButton mLoopButton;

   private HardwareButton mRewindButton;

   private HardwareButton mClearSoloButton;

   private HardwareButton mClearMuteButton;

   private HardwareButton mTrackModeButton;

   private HardwareButton mPluginModeButton;

   private HardwareButton mSendsModeButton;

   private HardwareButton mPanModeButton;

   private HardwareButton mScrollLeftButton;

   private HardwareButton mScrollRightButton;

   private RelativeHardwareKnob mTransportEncoder;

   private HardwareButton mChannelButton;

   private HardwareButton mZoomButton;

   private HardwareButton mScrollButton;

   private HardwareButton mBankButton;

   private HardwareButton mSectionButton;

   private HardwareButton mMarkerButton;

   private HardwareButton mAutomationOffButton;

   private HardwareButton mAutomationWriteButton;

   private HardwareButton mAutomationTouchButton;

   private HardwareButton mFastForwardButton;

   public PresonusFaderPort(final PresonusFaderPortDefinition definition, final ControllerHost host)
   {
      super(definition, host);

      mChannelCount = definition.channelCount();
      mChannels = new Channel[mChannelCount];
      mSysexHeader = "F0 00 01 06 " + definition.sysexDeviceID();
   }

   @Override
   public void init()
   {

      final ControllerHost host = getHost();
      mApplication = host.createApplication();
      mArranger = host.createArranger();
      mCueMarkerBank = mArranger.createCueMarkerBank(1);

      mMidiIn = host.getMidiInPort(0);
      mMidiIn.setMidiCallback((ShortMidiMessageReceivedCallback)this::onMidi);

      mMidiOut = host.getMidiOutPort(0);

      mCursorTrack = host.createCursorTrack(0, 0);

      mTrackBank = host.createTrackBank(mChannelCount, 1, 0, false);
      mMasterTrack = host.createMasterTrack(0);

      mCursorDevice = mCursorTrack.createCursorDevice("main", "Main", 0,
         CursorDeviceFollowMode.FOLLOW_SELECTION);

      mRemoteControls = mCursorDevice.createCursorRemoteControlsPage(8);

      if (mChannelCount > 8)
         mRemoteControls2 = mCursorDevice.createCursorRemoteControlsPage("right", 8, "");

      mTransport = host.createTransport();

      mClearMute = mApplication.getAction("clear_mute");
      mClearSolo = mApplication.getAction("clear_solo");

      // Link all send positions to the first
      mTrackBank.getItemAt(0).sendBank().scrollPosition().addValueObserver(p -> {
         for (int i = 1; i < mChannelCount; i++)
            mTrackBank.getItemAt(i).sendBank().scrollPosition().set(p);
      });

      // Automation Write Modes
      mTransport.isArrangerAutomationWriteEnabled().markInterested();
      mTransport.automationWriteMode().markInterested();

      initHardwareSurface();
      initLayers();

      runningStatusTimer();

      initDeviceMode();
   }

   private void initHardwareSurface()
   {
      mHardwareSurface = getHost().createHardwareSurface();

      mArmButton = createToggleButton("arm", 0);
      mMasterButton = createToggleButton("master", 0x3A);
      mShiftLeftButton = createToggleButton("shift_left", 0x06);
      mShiftRightButton = createToggleButton("shift_right", 0x46);
      mPlayButton = createToggleButton("play", 0x5e);
      mStopButton = createToggleButton("stop", 0x5d);
      mRecordButton = createToggleButton("record", 0x5f);
      mMetronomeButton = createToggleButton("metronome", 0x3B);
      mLoopButton = createToggleButton("loop", 0x56);
      mRewindButton = createToggleButton("rewind", 0x5B);
      mFastForwardButton = createToggleButton("fast_forward", 0x5C);
      mClearSoloButton = createToggleButton("clear_solo", 0x01);
      mClearMuteButton = createToggleButton("clear_mute", 0x02);

      mTrackModeButton = createToggleButton("track_mode", 0x28);
      mPluginModeButton = createToggleButton("plugin_mode", 0x2B);
      mSendsModeButton = createToggleButton("sends_mode", 0x29);
      mPanModeButton = createToggleButton("pan_mode", 0x2A);

      mScrollLeftButton = createToggleButton("scroll_left", 0x2E);
      mScrollRightButton = createToggleButton("scroll_right", 0x2F);

      mChannelButton = createToggleButton("channel", 0x36);
      mZoomButton = createToggleButton("zoom", 0x37);
      mScrollButton = createToggleButton("scroll", 0x38);
      mBankButton = createToggleButton("bank", 0x39);
      mSectionButton = createToggleButton("section", 0x3C);
      mMarkerButton = createToggleButton("marker", 0x3D);

      mDisplayEncoder = createClickEncoder("display", 0x20, 0x10);
      mTransportEncoder = createClickEncoder("transport", 0x53, 0x3C);

      mAutomationOffButton = createRGBButton("automation_on_off", 0x4F);
      mAutomationWriteButton = createRGBButton("automation_write", 0x4B);
      mAutomationTouchButton = createRGBButton("automation_touch", 0x4D);

      for (int index = 0; index < mChannelCount; index++)
      {
         final Channel channel = new Channel(index);

         mChannels[index] = channel;
      }

      initHardwareLayout();
   }

   private void initHardwareLayout()
   {
      mHardwareSurface.setPhysicalSize(500, 300);

      final HardwareSurface surface = mHardwareSurface;

      surface.hardwareControlWithId("shift_left").setBounds(19.584569732937684, 157.2700296735904,
         12.373887240356083, 12.373887240356083);
      surface.hardwareControlWithId("shift_right").setBounds(387.2403560830858, 156.37982195845686,
         13.264094955489615, 12.373887240356083);
      surface.hardwareControlWithId("play").setBounds(434.3478260869562, 190.86956521739123,
         21.739130434782613, 21.30434782608696);
      surface.hardwareControlWithId("stop").setBounds(408.2608695652173, 186.08695652173927,
         12.173913043478262, 13.478260869565219);
      surface.hardwareControlWithId("record").setBounds(469.13043478260795, 185.21739130434793,
         14.782608695652176, 13.913043478260871);
      surface.hardwareControlWithId("loop").setBounds(418.2608695652175, 167.82608695652175,
         11.73913043478261, 13.478260869565219);
      surface.hardwareControlWithId("fast_forward").setBounds(459.56521739130403, 168.69565217391317,
         12.608695652173914, 12.173913043478262);
      surface.hardwareControlWithId("channel").setBounds(407.4183976261123, 124.92581602373893,
         13.560830860534125, 8.516320474777448);
      surface.hardwareControlWithId("zoom").setBounds(427.8931750741839, 124.92581602373888,
         13.857566765578635, 9.109792284866469);
      surface.hardwareControlWithId("section").setBounds(448.3679525222552, 139.76261127596416,
         13.264094955489615, 9.70326409495549);
      surface.hardwareControlWithId("marker").setBounds(468.5459940652815, 139.76261127596425,
         13.857566765578635, 9.109792284866469);
      surface.hardwareControlWithId("mute1").setBounds(48.2228121526566, 81.57715839564915, 6.439169139465875,
         7.032640949554896);
      surface.hardwareControlWithId("select1").setBounds(49.95891585138779, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader1").setBounds(49.565217391304365, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute2").setBounds(68.65109223423386, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select2").setBounds(70.37516625779578, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader2").setBounds(70.04347826086953, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("solo3").setBounds(117.92096473856886, 99.60393698460072,
         19.8176336712677, 5.652173913043477);
      surface.hardwareControlWithId("mute3").setBounds(89.07937231581111, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select3").setBounds(90.79141666420377, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader3").setBounds(90.5217391304347, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute4").setBounds(109.50765239738837, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select4").setBounds(111.20766707061175, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader4").setBounds(110.99999999999986, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute5").setBounds(129.93593247896564, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select5").setBounds(131.62391747701975, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader5").setBounds(131.47826086956502, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute6").setBounds(150.3642125605429, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select6").setBounds(152.04016788342773, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader6").setBounds(151.95652173913018, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute7").setBounds(170.79249264212018, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select7").setBounds(172.4564182898357, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader7").setBounds(172.43478260869534, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute8").setBounds(191.22077272369745, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select8").setBounds(192.8726686962437, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader8").setBounds(192.9130434782605, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute9").setBounds(211.64905280527472, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select9").setBounds(213.28891910265168, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader9").setBounds(213.39130434782567, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute10").setBounds(232.077332886852, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select10").setBounds(233.70516950905966, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader10").setBounds(233.86956521739083, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute11").setBounds(252.50561296842926, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select11").setBounds(254.12141991546764, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader11").setBounds(254.347826086956, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute12").setBounds(272.93389305000653, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select12").setBounds(274.5376703218757, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader12").setBounds(274.8260869565211, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute13").setBounds(293.3621731315838, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select13").setBounds(294.9539207282837, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader13").setBounds(295.30434782608626, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute14").setBounds(313.7904532131611, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select14").setBounds(315.37017113469176, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader14").setBounds(315.7826086956514, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute15").setBounds(334.21873329473834, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select15").setBounds(335.7864215410998, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader15").setBounds(336.2608695652165, 112.17391304347824,
         14.34782608695652, 130.0);
      surface.hardwareControlWithId("mute16").setBounds(354.6470133763156, 81.57715839564915,
         6.439169139465875, 7.032640949554896);
      surface.hardwareControlWithId("select16").setBounds(356.20267194750784, 61.79440191528981,
         13.478260869565219, 10.0);
      surface.hardwareControlWithId("fader16").setBounds(356.73913043478166, 112.17391304347824,
         14.34782608695652, 130.0);

   }

   private HardwareButton createToggleButton(final String id, final int note)
   {
      final HardwareButton button = createButton(id, note);

      final OnOffHardwareLight light = mHardwareSurface.createOnOffHardwareLight();
      button.setBackgroundLight(light);

      light.isOn().onUpdateHardware(isOn -> {
         mMidiOut.sendMidi(0x90, note, isOn ? 127 : 0);
      });

      return button;
   }

   private HardwareButton createRGBButton(final String id, final int note)
   {
      final HardwareButton button = createButton(id, note);

      final MultiStateHardwareLight light = mHardwareSurface
         .createMultiStateHardwareLight(PresonusFaderPort::stateToColor);
      button.setBackgroundLight(light);

      light.setColorToStateFunction(PresonusFaderPort::colorToState);

      final IntConsumer sendState = new IntConsumer()
      {
         @Override
         public void accept(final int state)
         {
            for (int i = 0; i < 4; i++)
            {
               final int shift = 24 - 8 * i;
               final int byteValue = (state & (0x7F << shift)) >> shift;

               assert byteValue >= 0 && byteValue <= 127;

               if (mLastSent[i] != byteValue)
               {
                  mMidiOut.sendMidi(0x90 + i, note, byteValue);
                  mLastSent[i] = byteValue;
               }
            }
         }

         private final int[] mLastSent = new int[] { -1, -1, -1, -1 };
      };

      light.state().onUpdateHardware(sendState);

      return button;
   }

   private HardwareButton createButton(final String id, final int note)
   {
      final HardwareButton button = mHardwareSurface.createHardwareButton(id);

      button.pressedAction().setActionMatcher(mMidiIn.createNoteOnActionMatcher(0, note));
      button.releasedAction().setActionMatcher(mMidiIn.createNoteOffActionMatcher(0, note));

      return button;
   }

   private HardwareSlider createMotorFader(final int channel)
   {
      final HardwareSlider fader = mHardwareSurface.createHardwareSlider("fader" + (channel + 1));

      fader.setAdjustValueMatcher(mMidiIn.createAbsolutePitchBendValueMatcher(channel));

      fader.beginTouchAction().setActionMatcher(
         mMidiIn.createActionMatcher("status == 0x90 && data1 == " + (0x68 + channel) + " && data2 > 0"));
      fader.endTouchAction().setActionMatcher(
         mMidiIn.createActionMatcher("status == 0x90 && data1 == " + (0x68 + channel) + " && data2 == 0"));

      fader.isBeingTouched().markInterested();
      fader.targetValue().markInterested();

      final DoubleValueChangedCallback moveFader = new DoubleValueChangedCallback()
      {

         @Override
         public void valueChanged(final double value)
         {
            getHost().println("Moving fader to " + value);

            final int faderValue = Math.max(0, Math.min(16383, (int)(value * 16384.0)));

            if (mLastSentValue != value)
            {
               mMidiOut.sendMidi(0xE0 | channel, faderValue & 0x7f, faderValue >> 7);
               mLastSentValue = faderValue;
            }
         }

         private int mLastSentValue = -1;
      };

      fader.targetValue().addValueObserver(moveFader);

      // TODO: Register move fader callback somehow

      return fader;
   }

   private RelativeHardwareKnob createClickEncoder(final String id, final int key, final int CC)
   {
      final RelativeHardwareKnob encoder = mHardwareSurface.createRelativeHardwareKnob(id);

      encoder.setAdjustValueMatcher(mMidiIn.createRelativeSignedBitCCValueMatcher(0, CC));

      encoder.setSensitivity(128.0 / 100.0);
      encoder.setStepSize(1 / 100.0);

      final HardwareButton clickButton = mHardwareSurface.createHardwareButton(id + "_click");

      clickButton.pressedAction().setActionMatcher(mMidiIn.createNoteOnActionMatcher(0, key));
      clickButton.releasedAction().setActionMatcher(mMidiIn.createNoteOffActionMatcher(0, key));

      encoder.setHardwareButton(clickButton);

      return encoder;
   }

   private static int colorToState(final Color c)
   {
      if (c == null) // Off
         return 0;

      return 0x7F000000 | colorPartFromDouble(c.getRed()) << 16 | colorPartFromDouble(c.getGreen()) << 8
         | colorPartFromDouble(c.getBlue());
   }

   private static Color stateToColor(final int state)
   {
      final int red = (state & 0x7F0000) >> 16;
      final int green = (state & 0x7F00) >> 8;
      final int blue = (state & 0x7F);

      return Color.fromRGB(red / 127.0, green / 127.0, blue / 127.0);
   }

   private static int colorPartFromDouble(final double x)
   {
      return Math.max(0, Math.min((int)(127.0 * x), 127));
   }

   private void initLayers()
   {
      mDefaultLayer = createLayer("Default");
      mSendsLayer = createLayer("Sends");
      mPanLayer = createLayer("Pan");
      mTrackLayer = createLayer("Track");
      mChannelLayer = createLayer("Channel");
      mMarkerLayer = createLayer("Marker");
      mDeviceLayer = createLayer("Device");
      mZoomLayer = createLayer("Zoom");
      mScrollLayer = createLayer("Scroll");
      mBankLayer = createLayer("Bank");
      mMasterLayer = createLayer("Master");
      mSectionLayer = createLayer("Section");

      new LayerGroup(mTrackLayer, mDeviceLayer, mSendsLayer, mPanLayer);

      new LayerGroup(mChannelLayer, mZoomLayer, mScrollLayer, mBankLayer, mSectionLayer, mMarkerLayer);

      initDefaultLayer();
      initSendsLayer();
      initChannelLayer();
      initMarkerLayer();
      initDeviceLayer();
      initZoomLayer();
      initScrollLayer();
      initBankLayer();
      initMasterLayer();
      initSectionLayer();

      mDefaultLayer.activate();
      mTrackLayer.activate();

      // DebugUtilities.createDebugLayer(mLayers, mHardwareSurface).activate();
   }

   private Layer createLayer(final String name)
   {
      return new Layer(mLayers, name);
   }

   private void initDefaultLayer()
   {
      mDefaultLayer.bindPressed(mArmButton, () -> mArm = !mArm);
      mDefaultLayer.bind(() -> mArm, mArmButton);

      mDefaultLayer.bindToggle(mMasterButton, mMasterLayer);

      mDefaultLayer.bind(mDisplayEncoder, mCursorTrack.pan());
      mDefaultLayer.bindPressed(mDisplayEncoder, mCursorTrack.pan()::reset);

      mDefaultLayer.bindPressed(mShiftLeftButton, () -> mShift = true);
      mDefaultLayer.bindReleased(mShiftLeftButton, () -> mShift = false);
      mDefaultLayer.bind(() -> mShift, mShiftLeftButton);

      mDefaultLayer.bindPressed(mShiftRightButton, () -> mShift = true);
      mDefaultLayer.bindReleased(mShiftRightButton, () -> mShift = false);
      mDefaultLayer.bind(() -> mShift, mShiftRightButton);

      mDefaultLayer.bindToggle(mPlayButton, mTransport.isPlaying());
      mDefaultLayer.bindToggle(mStopButton, mTransport.stopAction(), () -> !mTransport.isPlaying().get());
      mDefaultLayer.bindToggle(mRecordButton, mTransport.isArrangerRecordEnabled());
      mDefaultLayer.bindToggle(mMetronomeButton, mTransport.isMetronomeEnabled());
      mDefaultLayer.bindToggle(mLoopButton, mTransport.isArrangerLoopEnabled());
      mDefaultLayer.bindPressed(mRewindButton, mTransport.rewindAction());
      mDefaultLayer.bindPressed(mFastForwardButton, mTransport.fastForwardAction());
      mDefaultLayer.bindToggle(mClearSoloButton, mClearSolo, mClearSolo.isEnabled());
      mDefaultLayer.bindToggle(mClearMuteButton, mClearMute, mClearMute.isEnabled());

      mDefaultLayer.bindToggle(mTrackModeButton, mTrackLayer);
      mDefaultLayer.bindToggle(mPluginModeButton, mDeviceLayer);
      mDefaultLayer.bindToggle(mSendsModeButton, mSendsLayer);
      mDefaultLayer.bindToggle(mPanModeButton, mPanLayer);

      mDefaultLayer.bindToggle(mScrollLeftButton, mTrackBank.scrollBackwardsAction(),
         mTrackBank.canScrollBackwards());
      mDefaultLayer.bindToggle(mScrollRightButton, mTrackBank.scrollForwardsAction(),
         mTrackBank.canScrollForwards());

      mDefaultLayer.bindToggle(mChannelButton, mChannelLayer);
      mDefaultLayer.bindToggle(mZoomButton, mZoomLayer);
      mDefaultLayer.bindToggle(mScrollButton, mScrollLayer);
      mDefaultLayer.bindToggle(mBankButton, mBankLayer);
      mDefaultLayer.bindToggle(mScrollButton, mSectionLayer);
      mDefaultLayer.bindToggle(mMarkerButton, mMarkerLayer);

      mDefaultLayer.bindPressed(mAutomationOffButton, mTransport.isArrangerAutomationWriteEnabled());
      mDefaultLayer.bind(() -> {
         final boolean isEnabled = mTransport.isArrangerAutomationWriteEnabled().get();
         return isEnabled ? ARM_HIGH : DIM_WHITE;
      }, mAutomationOffButton);

      mDefaultLayer.bindPressed(mAutomationWriteButton, () -> mTransport.automationWriteMode().set("write"));
      mDefaultLayer.bind(() -> {
         if (mTransport.automationWriteMode().get().equals("write"))
         {
            final boolean isEnabled = mTransport.isArrangerAutomationWriteEnabled().get();
            return isEnabled ? ARM_HIGH : DIM_WHITE;
         }

         return null;
      }, mAutomationWriteButton);

      mDefaultLayer.bindPressed(mAutomationTouchButton, () -> mTransport.automationWriteMode().set("touch"));
      mDefaultLayer.bind(() -> {
         if (mTransport.automationWriteMode().get().equals("touch"))
         {
            final boolean isEnabled = mTransport.isArrangerAutomationWriteEnabled().get();
            return isEnabled ? ARM_HIGH : DIM_WHITE;
         }

         return null;
      }, mAutomationTouchButton);

      for (int channelIndex = 0; channelIndex < mChannelCount; channelIndex++)
      {
         final Track track = mTrackBank.getItemAt(channelIndex);
         bindTrack(mDefaultLayer, channelIndex, track);
      }
   }

   private void initChannelLayer()
   {
      mChannelLayer.bind(mTransportEncoder, mCursorTrack);
   }

   private void initSendsLayer()
   {
      mSendsLayer.bind(mDisplayEncoder, mTrackBank.getItemAt(0).sendBank());
   }

   private void initBankLayer()
   {
      mBankLayer.bind(mTransportEncoder, mTrackBank);
      mBankLayer.bindPressed(mTransportEncoder,
         () -> mTrackBank.scrollIntoView(mCursorTrack.position().get()));
   }

   private void initScrollLayer()
   {
      mScrollLayer.bindPressed(mTransportEncoder, mApplication.zoomToFitAction());
   }

   private void initMarkerLayer()
   {
      mMarkerLayer.bindToggle(mScrollLeftButton, mCueMarkerBank.scrollPageBackwardsAction(),
         mCueMarkerBank.canScrollBackwards());
      mMarkerLayer.bindToggle(mScrollRightButton, mCueMarkerBank.scrollPageForwardsAction(),
         mCueMarkerBank.canScrollForwards());

      mMarkerLayer.bindPressed(mTransportEncoder, () -> mCueMarkerBank.getItemAt(0).launch(true));
      mMarkerLayer.bind(mTransportEncoder, mCueMarkerBank);
   }

   private void initDeviceLayer()
   {
      for (int c = 0; c < mChannelCount; c++)
      {
         final int channelIndex = c;
         final int indexInGroup = c & 0x7;

         final RemoteControl parameter = c >= 8 ? mRemoteControls2.getParameter(indexInGroup)
            : mRemoteControls.getParameter(indexInGroup);

         parameter.name().markInterested();
         parameter.displayedValue().markInterested();

         final Channel channel = mChannels[c];

         mDeviceLayer.bind(channel.motorFader, parameter);

         mDeviceLayer.bindPressed(channel.mute, parameter::reset);

         mDeviceLayer.bindPressed(channel.select, () -> getPageIndex().set(channelIndex));
         mDeviceLayer.bind(() -> {
            if (channelIndex < mRemoteControls.pageNames().get().length)
            {
               if (getPageIndex().get() == channelIndex)
               {
                  return WHITE;
               }

               if (mRemoteControls2 != null)
               {
                  if (mRemoteControls.selectedPageIndex().get() == channelIndex
                     || mRemoteControls2.selectedPageIndex().get() == channelIndex)
                  {
                     return HALF_WHITE;
                  }
               }

               return DIM_WHITE;
            }

            return null;
         }, channel.select);
      }
   }

   private void initZoomLayer()
   {
      mZoomLayer.bind(mTransportEncoder, mApplication.zoomInAction(), mApplication.zoomOutAction());
      mZoomLayer.bindPressed(mTransportEncoder, mApplication.zoomToSelectionAction());
   }

   private void initMasterLayer()
   {
      bindTrack(mMasterLayer, mChannelCount - 1, mMasterTrack);
   }

   private void initSectionLayer()
   {

   }

   private void onMidi(final ShortMidiMessage data)
   {
      // getHost().println(data.toString());
   }

   @Override
   public void flush()
   {
      mHardwareSurface.updateHardware();

      for (int index = 0; index < mChannelCount; index++)
      {
         mChannels[index].display.updateHardware();
      }
   }

   private SettableIntegerValue getPageIndex()
   {
      if (mShift && mRemoteControls2 != null)
      {
         return mRemoteControls2.selectedPageIndex();
      }
      return mRemoteControls.selectedPageIndex();
   }

   private void initDeviceMode()
   {
      mRemoteControls.pageNames().markInterested();
      mRemoteControls.selectedPageIndex().markInterested();

      if (mRemoteControls2 != null)
      {
         mRemoteControls2.selectedPageIndex().markInterested();
      }

      for (int c = 0; c < mChannelCount; c++)
      {
         final int channelIndex = c;
         final int indexInGroup = c & 0x7;

         final RemoteControl parameter = c >= 8 ? mRemoteControls2.getParameter(indexInGroup)
            : mRemoteControls.getParameter(indexInGroup);

         parameter.name().markInterested();
         parameter.displayedValue().markInterested();
         parameter.value().markInterested();

         final Channel channel = mChannels[c];

         mDeviceLayer.bind(channel.display, new DisplayTarget()
         {
            @Override
            public int getBarValue()
            {
               if (channelIndex < 8)
               {
                  return ValueUtils.doubleToUnsigned7(parameter.value().get());
               }
               return 0;
            }

            @Override
            public String getText(final int line)
            {
               if (line == 0)
               {
                  final String[] pageNames = mRemoteControls.pageNames().get();

                  if (channelIndex == 15)
                  {
                     final int rightIndex = mRemoteControls2.selectedPageIndex().get();

                     if (rightIndex < 0)
                        return "";

                     return rightIndex < pageNames.length ? pageNames[rightIndex] : "";
                  }

                  if (channelIndex < pageNames.length)
                  {
                     return pageNames[channelIndex];
                  }
               }

               if (line == 2)
                  return parameter.name().getLimited(7);
               if (line == 3)
                  return parameter.displayedValue().getLimited(7);

               return null;
            }

            @Override
            public DisplayMode getMode()
            {
               return DisplayMode.SmallText;
            }

            @Override
            public boolean isTextInverted(final int line)
            {
               if (line == 0 && getPageIndex().get() == channelIndex)
               {
                  return true;
               }

               if (line == 0 && channelIndex == 15)
               {
                  return true;
               }

               return false;
            }
         });
      }
   }

   private Channel[] mChannels;

   class Channel
   {

      public Channel(final int index)
      {
         super();

         final int channelNumber = index + 1;

         solo = createToggleButton("solo" + channelNumber, SOLOD_IDS[index]);
         mute = createToggleButton("mute" + channelNumber, (index >= 8 ? 0x70 : 0x10) + index);
         select = createRGBButton("select" + channelNumber, SELECT_IDS[index]);
         motorFader = createMotorFader(index);
         display = new Display(index, mSysexHeader, PresonusFaderPort.this);
      }

      final HardwareButton solo;

      final HardwareButton mute;

      final HardwareButton select;

      final HardwareSlider motorFader;

      final Display display;
   }

   private void bindTrack(final Layer layer, final int index, final Track track)
   {
      track.position().markInterested();
      track.name().markInterested();
      track.color().markInterested();
      final Parameter volume = track.volume();
      volume.markInterested();
      volume.displayedValue().markInterested();
      track.mute().markInterested();
      track.solo().markInterested();
      track.arm().markInterested();
      track.exists().markInterested();
      track.pan().markInterested();
      volume.markInterested();
      track.pan().name().markInterested();
      track.pan().displayedValue().markInterested();

      if (track != mMasterTrack)
      {
         track.sendBank().getItemAt(0).name().markInterested();
         track.sendBank().getItemAt(0).displayedValue().markInterested();
      }

      final Channel channel = mChannels[index];
      final HardwareButton solo = channel.solo;
      final HardwareButton mute = channel.mute;
      final HardwareButton select = channel.select;
      final HardwareSlider motorFader = channel.motorFader;
      final Display display = channel.display;

      layer.bindToggle(solo, track.solo());
      layer.bindToggle(mute, track.mute());

      final BooleanValue isSelected = mCursorTrack.createEqualsValue(track);

      if (track == mMasterTrack)
      {
         layer.bind(motorFader, volume);
      }
      else
      {
         mTrackLayer.bind(motorFader, volume);
         mPanLayer.bind(motorFader, track.pan());
         mSendsLayer.bind(motorFader, track.sendBank().getItemAt(0));
      }

      layer.bindPressed(select, () -> {
         if (mArm)
            track.arm().toggle();
         else
            mCursorTrack.selectChannel(track);
      });

      layer.bind((Supplier<Color>)() -> {
         if (track.exists().get())
         {
            if (mArm)
            {
               return track.arm().get() ? ARM_HIGH : ARM_LOW;
            }
            else
            {
               if (isSelected.get())
                  return WHITE;

               return track.color().get();
            }
         }

         return BLACK;
      }, select);

      if (track == mMasterTrack)
      {
         layer.bind(display, new ChannelDisplayTarget(track, isSelected, motorFader));
      }
      else
      {
         mTrackLayer.bind(display, new ChannelDisplayTarget(track, isSelected, motorFader));
         mSendsLayer.bind(display, new SendDisplayTarget(track, isSelected, motorFader));
         mPanLayer.bind(display, new PanDisplayTarget(track, isSelected, motorFader));
      }
   }

   private void runningStatusTimer()
   {
      getMidiOutPort(0).sendMidi(0xA0, 0, 0);

      getHost().scheduleTask(this::runningStatusTimer, 1000);
   }

   @Override
   public void exit()
   {
   }

   /* API Objects */
   private CursorTrack mCursorTrack;

   private PinnableCursorDevice mCursorDevice;

   private CursorRemoteControlsPage mRemoteControls;

   private Transport mTransport;

   private MidiOut mMidiOut;

   private Application mApplication;

   private boolean mShift;

   private final int mChannelCount;

   private TrackBank mTrackBank;

   private boolean mArm;

   private final String mSysexHeader;

   private Layer mDefaultLayer;

   private Layer mTrackLayer;

   private Layer mDeviceLayer;

   private Layer mSendsLayer;

   private Layer mPanLayer;

   private Layer mChannelLayer;

   private Layer mZoomLayer;

   private Layer mScrollLayer;

   private Layer mBankLayer;

   private Layer mMasterLayer;

   private Layer mSectionLayer;

   private Layer mMarkerLayer;

   private static class PanDisplayTarget extends ChannelDisplayTarget
   {
      public PanDisplayTarget(
         final Track track,
         final BooleanValue isSelected,
         final HardwareSlider motorFader)
      {
         super(track, isSelected, motorFader);
      }

      @Override
      protected Parameter getMainControl()
      {
         return mTrack.pan();
      }
   };

   private static class SendDisplayTarget extends ChannelDisplayTarget
   {
      public SendDisplayTarget(
         final Track track,
         final BooleanValue isSelected,
         final HardwareSlider motorFader)
      {
         super(track, isSelected, motorFader);
      }

      @Override
      protected Parameter getMainControl()
      {
         return mTrack.sendBank().getItemAt(0);
      }

      @Override
      protected Parameter getLabelControl()
      {
         return mTrack.sendBank().getItemAt(0);
      }
   };

   private MasterTrack mMasterTrack;

   private Arranger mArranger;

   private CueMarkerBank mCueMarkerBank;

   private Action mClearMute;

   private Action mClearSolo;

   private CursorRemoteControlsPage mRemoteControls2;

   // Hardware

   HardwareSurface mHardwareSurface;

   private HardwareButton mArmButton;

   private MidiIn mMidiIn;

   private final Layers mLayers = new Layers(this);
}
