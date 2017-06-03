package axoloti.piccolo.parameterviews;

import axoloti.Preset;
import axoloti.Theme;
import axoloti.datatypes.Value;
import axoloti.objectviews.IAxoObjectInstanceView;
import axoloti.parameters.ParameterInstanceFrac32UMap;
import axoloti.piccolo.PatchPNode;
import components.piccolo.PAssignMidiCCComponent;
import components.piccolo.PAssignMidiCCMenuItems;
import components.piccolo.PAssignModulatorComponent;
import components.piccolo.PAssignModulatorMenuItems;
import components.piccolo.PAssignPresetComponent;
import components.piccolo.control.PDialComponent;
import java.awt.Graphics2D;
import javax.swing.BoxLayout;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import org.piccolo2d.util.PPaintContext;

public class PParameterInstanceViewFrac32UMap extends PParameterInstanceViewFrac32U {

    PAssignModulatorComponent modulationAssign;
    PAssignPresetComponent presetAssign;

    public PParameterInstanceViewFrac32UMap(ParameterInstanceFrac32UMap parameterInstance, IAxoObjectInstanceView axoObjectInstanceView) {
        super(parameterInstance, axoObjectInstanceView);
    }

    @Override
    public ParameterInstanceFrac32UMap getParameterInstance() {
        return (ParameterInstanceFrac32UMap) parameterInstance;
    }

    @Override
    public PDialComponent CreateControl() {
        PDialComponent d = new PDialComponent(
                0.0,
                getParameterInstance().getMin(),
                getParameterInstance().getMax(),
                getParameterInstance().getTick(), axoObjectInstanceView);
        d.setNative(getParameterInstance().getConvs());
        return d;
    }

    @Override
    public void PostConstructor() {
        super.PostConstructor();

        PatchPNode btns = new PatchPNode(getPatchView());
        btns.setLayout(new BoxLayout(btns.getProxyComponent(), BoxLayout.PAGE_AXIS));

        //lblCC = new LabelComponent("C");
        //btns.add(lblCC);
        midiAssign = new PAssignMidiCCComponent(this);

        btns.addChild(midiAssign);
        modulationAssign = new PAssignModulatorComponent(this);
        btns.addChild(modulationAssign);
        presetAssign = new PAssignPresetComponent(this);
        btns.addChild(presetAssign);
        addChild(btns);
        updatePresetAssignVisibility(false);
        updateModulationAssignVisibility(false);

//        setComponentPopupMenu(new ParameterInstanceUInt7MapPopupMenu3(this));
        addInputEventListener(popupMouseListener);
        updateV();
    }

    @Override
    public void updateV() {
        super.updateV();
        if (ctrl != null) {
            ctrl.setValue(getParameterInstance().getValue().getDouble());
        }
    }

    private void updatePresetAssignVisibility() {
        updatePresetAssignVisibility(true);
    }

    private void updateModulationAssignVisibility() {
        updateModulationAssignVisibility(true);
    }

    private void updatePresetAssignVisibility(boolean repaint) {
        presetAssign.setVisible((getParameterInstance().getPresets() != null)
                && (!getParameterInstance().getPresets().isEmpty()));
        if (repaint && presetAssign.getVisible()) {
            presetAssign.repaint();
        }
    }

    private void updateModulationAssignVisibility(boolean repaint) {
        if (modulationAssign != null) {
            modulationAssign.setVisible(getParameterInstance().getModulators() != null);
            if (repaint && modulationAssign.getVisible()) {
                modulationAssign.repaint();
            }
        }
    }

    /*
     *  Preset logic
     */
    @Override
    public void ShowPreset(int i) {
        this.presetEditActive = i;
        if (i > 0) {
            Preset p = getParameterInstance().GetPreset(presetEditActive);
            if (p != null) {
                setPaint(Theme.getCurrentTheme().Parameter_Preset_Highlight);
                ctrl.setValue(p.value.getDouble());
            } else {
                setPaint(Theme.getCurrentTheme().Parameter_Default_Background);
                ctrl.setValue(getParameterInstance().getValue().getDouble());
            }
        } else {
            setPaint(Theme.getCurrentTheme().Parameter_Default_Background);
            ctrl.setValue(getParameterInstance().getValue().getDouble());
        }
        updatePresetAssignVisibility();
    }

    @Override
    public void populatePopup(JPopupMenu m) {
        super.populatePopup(m);
        JMenu m1 = new JMenu("Midi CC");
        new PAssignMidiCCMenuItems(this, m1);
        m.add(m1);
        JMenu m2 = new JMenu("Modulation");
        new PAssignModulatorMenuItems(this, m2);
        m.add(m2);
    }

    @Override
    public PDialComponent getControlComponent() {
        return (PDialComponent) ctrl;
    }

    @Override
    public void updateModulation(int index, double amount) {
        getParameterInstance().updateModulation(index, amount);
        updateModulationAssignVisibility();
    }

    @Override
    public Preset AddPreset(int index, Value value) {
        Preset p = getParameterInstance().AddPreset(index, value);
        updatePresetAssignVisibility();
        return p;
    }

    @Override
    public void RemovePreset(int index) {
        getParameterInstance().RemovePreset(index);
        updatePresetAssignVisibility();
    }

    @Override
    protected void paint(PPaintContext paintContext) {
        super.paint(paintContext);
        Graphics2D g2 = paintContext.getGraphics();
        if (parameterInstance.isOnParent()) {
            ctrl.setForeground(Theme.getCurrentTheme().Parameter_On_Parent_Highlight);
        } else {
            ctrl.setForeground(Theme.getCurrentTheme().Parameter_Default_Foreground);
        }
    }
}