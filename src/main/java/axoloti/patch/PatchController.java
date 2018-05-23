package axoloti.patch;

import axoloti.Axoloti;
import axoloti.Modulation;
import axoloti.Modulator;
import axoloti.codegen.patch.PatchViewCodegen;
import axoloti.connection.CConnection;
import axoloti.connection.IConnection;
import axoloti.datatypes.DataType;
import axoloti.mvc.AbstractController;
import axoloti.mvc.IView;
import axoloti.object.AxoObject;
import axoloti.object.AxoObjectFile;
import axoloti.object.AxoObjectFromPatch;
import axoloti.object.AxoObjectPatcher;
import axoloti.object.AxoObjectPatcherObject;
import axoloti.object.IAxoObject;
import axoloti.object.inlet.Inlet;
import axoloti.objectlibrary.AxoObjects;
import static axoloti.patch.PatchModel.USE_EXECUTION_ORDER;
import axoloti.patch.net.Net;
import axoloti.patch.object.AxoObjectInstance;
import axoloti.patch.object.AxoObjectInstanceAbstract;
import axoloti.patch.object.AxoObjectInstanceFactory;
import axoloti.patch.object.AxoObjectInstancePatcher;
import axoloti.patch.object.AxoObjectInstancePatcherObject;
import axoloti.patch.object.IAxoObjectInstance;
import axoloti.patch.object.inlet.InletInstance;
import axoloti.patch.object.iolet.IoletInstance;
import axoloti.patch.object.outlet.OutletInstance;
import axoloti.patch.object.parameter.ParameterInstance;
import axoloti.preferences.Preferences;
import axoloti.target.TargetModel;
import axoloti.target.fs.SDFileReference;
import axoloti.utils.Constants;
import java.awt.Point;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;
import qcmds.QCmdCompileModule;
import qcmds.QCmdCompilePatch;
import qcmds.QCmdProcessor;
import qcmds.QCmdRecallPreset;
import qcmds.QCmdUploadFile;

public class PatchController extends AbstractController<PatchModel, IView> {

    protected PatchController(PatchModel model) {
        super(model);
        init();
    }

    private void init() {

        // Now it is the time to cleanup the model, replace object instances with linked objects
        ArrayList<IAxoObjectInstance> unlinked_object_instances = new ArrayList<>(getModel().objectinstances);
        getModel().objectinstances.clear();
        for (IAxoObjectInstance unlinked_object_instance : unlinked_object_instances) {
            add_unlinked_objectinstance(unlinked_object_instance);
        }
        promoteOverloading(true);
    }

    public QCmdProcessor getQCmdProcessor() {
        return QCmdProcessor.getQCmdProcessor();
    }

    public void recallPreset(int i) {
        getQCmdProcessor().appendToQueue(new QCmdRecallPreset(i));
    }

    public void showPreset(int i) {
        // TODO: fix preset logic
        //patchView.ShowPreset(i);
    }

    public void compile() {
        for (String module : getModel().getModules()) {
            getQCmdProcessor().appendToQueue(new QCmdCompileModule(this,
                    module,
                    getModel().getModuleDir(module)));
        }
        getQCmdProcessor().appendToQueue(new QCmdCompilePatch(this));
    }

    public void uploadDependentFiles(String sdpath) {
        ArrayList<SDFileReference> files = getModel().getDependendSDFiles();
        for (SDFileReference fref : files) {
            File f = fref.localfile;
            if (f == null) {
                Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, "File not resolved: {0}", fref.targetPath);
                continue;
            }
            if (!f.exists()) {
                Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, "File does not exist: {0}", f.getName());
                continue;
            }
            if (!f.canRead()) {
                Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, "Can't read file {0}", f.getName());
                continue;
            }
            String targetfn = fref.targetPath;
            if (targetfn.isEmpty()) {
                Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, "Target filename empty {0}", f.getName());
                continue;
            }
            if (targetfn.charAt(0) != '/') {
                targetfn = sdpath + "/" + fref.targetPath;
            }
            if (!TargetModel.getTargetModel().getSDCardInfo().exists(targetfn, f.lastModified(), f.length())) {
                try {
                    getQCmdProcessor().appendToQueue(new qcmds.QCmdGetFileInfo(targetfn));
                    getQCmdProcessor().waitQueueFinished();
                    getQCmdProcessor().appendToQueue(new qcmds.QCmdPing());
                    getQCmdProcessor().waitQueueFinished();
                    if (!TargetModel.getTargetModel().getSDCardInfo().exists(targetfn, f.lastModified(), f.length())) {
                        if (f.length() > 8 * 1024 * 1024) {
                            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "file {0} is larger than 8MB, skip uploading", f.getName());
                            continue;
                        }
                        for (int i = 1; i < targetfn.length(); i++) {
                            if (targetfn.charAt(i) == '/') {
                                getQCmdProcessor().appendToQueue(new qcmds.QCmdCreateDirectory(targetfn.substring(0, i)));
                                getQCmdProcessor().waitQueueFinished();
                            }
                        }
                        getQCmdProcessor().appendToQueue(new QCmdUploadFile(f, targetfn));
                    } else {
                        Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "file {0} matches timestamp and size, skip uploading", f.getName());
                    }
                } catch (Exception ex) {
                    Logger.getLogger(PatchController.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "file {0} matches timestamp and size, skip uploading", f.getName());
            }
        }
    }

    String generateCode3() {
        Preferences prefs = Preferences.getPreferences();
        /* TODO: fix "controller object"
        controllerObjectInstance = null;
        String cobjstr = prefs.getControllerObject();
        if (prefs.isControllerEnabled() && cobjstr != null && !cobjstr.isEmpty()) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "Using controller object: {0}", cobjstr);
            AxoObjectAbstract x = null;
            ArrayList<AxoObjectAbstract> objs = MainFrame.axoObjects.GetAxoObjectFromName(cobjstr, GetCurrentWorkingDirectory());
            if ((objs != null) && (!objs.isEmpty())) {
                x = objs.get(0);
            }
            if (x != null) {
                controllerObjectInstance = x.CreateInstance(null, "ctrl0x123", new Point(0, 0));
            } else {
                Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "Unable to created controller for : {0}", cobjstr);
            }
        }
         */
        getModel().createIID();
        //TODO: (enhancement) use execution order, rather than UI ordering
        if (USE_EXECUTION_ORDER) {
            getModel().sortByExecution();
        } else {
            getModel().sortByPosition();
        }
        PatchViewCodegen codegen = new PatchViewCodegen(model);
        String c = codegen.generateCode4();
        return c;
    }

    public PatchViewCodegen writeCode() {
        String buildDir = System.getProperty(Axoloti.HOME_DIR) + "/build";
        return writeCode(buildDir + "/xpatch");
    }

    public PatchViewCodegen writeCode(String file_basename) {
//        String c = GenerateCode3();
        getModel().createIID();
        if (USE_EXECUTION_ORDER) {
            getModel().sortByExecution();
        } else {
            getModel().sortByPosition();
        }
        PatchViewCodegen codegen = new PatchViewCodegen(getModel());
        String c = codegen.generateCode4();
        try (FileOutputStream f = new FileOutputStream(file_basename + ".cpp")) {
            f.write(c.getBytes());
        } catch (FileNotFoundException ex) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, ex.toString());
        } catch (IOException ex) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, ex.toString());
        }
        Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "Generate code complete");
        return codegen;
    }

    public void uploadToFlash() {
        try {
            PatchController.this.writeCode();
            QCmdProcessor qcmdprocessor = QCmdProcessor.getQCmdProcessor();
            qcmdprocessor.appendToQueue(new qcmds.QCmdStop());
            qcmdprocessor.appendToQueue(new qcmds.QCmdCompilePatch(this));
            qcmdprocessor.waitQueueFinished();
            IConnection conn = CConnection.getConnection();
            byte[] bb = PatchFileBinary.getPatchFileBinary();
            // TODO: add test if it really fits in the flash partition, issue #409
            qcmdprocessor.appendToQueue(new qcmds.QCmdWriteMem(conn.getTargetProfile().getSDRAMAddr(), bb));
            qcmdprocessor.appendToQueue(new qcmds.QCmdCopyPatchToFlash());
        } catch (Exception ex) {
            Logger.getLogger(PatchController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void uploadToSDCard(String sdfilename) {
        try {
            PatchController.this.writeCode();
            Logger.getLogger(PatchController.class.getName()).log(Level.INFO, "sdcard filename:{0}", sdfilename);
            QCmdProcessor qcmdprocessor = QCmdProcessor.getQCmdProcessor();
            qcmdprocessor.appendToQueue(new qcmds.QCmdStop());
            for (String module : getModel().getModules()) {
                qcmdprocessor.appendToQueue(new QCmdCompileModule(this,
                        module,
                        getModel().getModuleDir(module)
                ));
            }
            qcmdprocessor.appendToQueue(new qcmds.QCmdCompilePatch(this));
            // create subdirs...

            for (int i = 1; i < sdfilename.length(); i++) {
                if (sdfilename.charAt(i) == '/') {
                    qcmdprocessor.appendToQueue(new qcmds.QCmdCreateDirectory(sdfilename.substring(0, i)));
                    qcmdprocessor.waitQueueFinished();
                }
            }
            qcmdprocessor.waitQueueFinished();
            Calendar cal = Calendar.getInstance();
            if (true) { // getModel().isDirty()) {
                // TODO: use time of last modification?
            } else {
                if (getFileNamePath() != null && !getFileNamePath().isEmpty()) {
                    File f = new File(getFileNamePath());
                    if (f.exists()) {
                        cal.setTimeInMillis(f.lastModified());
                    }
                }
            }
            qcmdprocessor.appendToQueue(new qcmds.QCmdUploadPatchSD(sdfilename, cal));

            Serializer serializer = new Persister();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256 * 1024);
            try {
                serializer.write(getModel(), baos);
            } catch (Exception ex) {
                Logger.getLogger(AxoObjects.class.getName()).log(Level.SEVERE, null, ex);
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            String sdfnPatch = sdfilename.substring(0, sdfilename.length() - 3) + "axp";
            qcmdprocessor.appendToQueue(new qcmds.QCmdUploadFile(bais, sdfnPatch));

            String dir;
            int i = sdfilename.lastIndexOf('/');
            if (i > 0) {
                dir = sdfilename.substring(0, i);
            } else {
                dir = "";
            }
            uploadDependentFiles(dir);
        } catch (Exception ex) {
            Logger.getLogger(PatchController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void uploadToSDCard() {
        PatchController.this.uploadToSDCard("/" + getSDCardPath() + "/patch.bin");
    }

    public void setFileNamePath(String FileNamePath) {
        setModelProperty(PatchModel.PATCH_FILENAME, FileNamePath);
    }

    public boolean delete(IAxoObjectInstance o) {
        boolean deletionSucceeded = false;
        if (o == null) {
            return deletionSucceeded;
        }
        disconnect(o);
        ((AxoObjectInstanceAbstract) o).dispose();
        int i;
        for (i = getModel().Modulators.size() - 1; i >= 0; i--) {
            Modulator m1 = getModel().Modulators.get(i);
            if (m1.objinst == o) {
                getModel().Modulators.remove(m1);
                for (Modulation mt : m1.Modulations) {
                    mt.destination.removeModulation(mt);
                }
            }
        }
        boolean succeeded = 
                removeUndoableElementFromList(PatchModel.PATCH_OBJECTINSTANCES, o);
//        if (succeeded) {
//            o.dispose();
//        }
        return succeeded;
    }

    public IAxoObjectInstance addObjectInstance(IAxoObject obj, Point loc) {
        if (true) {
            /*!isLocked()) {*/

            if (obj == null) {
                Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, "AddObjectInstance NULL");
                return null;
            }
            int i = 1;
            String n = obj.getDefaultInstanceName() + "_";
            while (getModel().findObjectInstance(n + i) != null) {
                i++;
            }
            IAxoObjectInstance objinst = AxoObjectInstanceFactory.createView(obj, getModel(), n + i, loc);

            Modulator[] m = obj.getModulators();
            if (m != null) {
                if (getModel().Modulators == null) {
                    getModel().Modulators = new ArrayList<>();
                }
                for (Modulator mm : m) {
                    mm.objinst = objinst;
                    getModel().Modulators.add(mm);
                }
            }
            addUndoableElementToList(PatchModel.PATCH_OBJECTINSTANCES, objinst);
            return objinst;
        } else {
            Logger.getLogger(PatchController.class.getName()).log(Level.INFO, "can't add connection: locked!");
            return null;
        }
    }

    public void fixNegativeObjectCoordinates() {
        int minx = 0;
        int miny = 0;
        for (IAxoObjectInstance o : getModel().getObjectInstances()) {
            Point p = o.getLocation();
            if (p.x < minx) {
                minx = p.x;
            }
            if (p.y < miny) {
                miny = p.y;
            }
        }
        if ((minx < 0) || (miny < 0)) {
            for (IAxoObjectInstance o : getModel().getObjectInstances()) {
                Point p = o.getLocation();
                o.getController().changeLocation(p.x - minx, p.y - miny);
            }
        }
    }

    public String getFileNamePath() {
        return getModel().getFileNamePath();
    }

    public String getSDCardPath() {
        String FileNameNoPath = getFileNamePath();
        String separator = System.getProperty("file.separator");
        int lastSeparatorIndex = FileNameNoPath.lastIndexOf(separator);
        if (lastSeparatorIndex > 0) {
            FileNameNoPath = FileNameNoPath.substring(lastSeparatorIndex + 1);
        }
        String FileNameNoExt = FileNameNoPath;
        if (FileNameNoExt.endsWith(".axp") || FileNameNoExt.endsWith(".axs") || FileNameNoExt.endsWith(".axh")) {
            FileNameNoExt = FileNameNoExt.substring(0, FileNameNoExt.length() - 4);
        }
        return FileNameNoExt;
    }

    public void setPresetUpdatePending(boolean updatePending) {
        getModel().presetUpdatePending = updatePending;
    }

    public boolean isPresetUpdatePending() {
        return getModel().presetUpdatePending;
    }

    public void showCompileFail() {
        getModel().setLocked(false);
    }

    private IAxoObjectInstance getObjectAtLocation(int x, int y) {
        for (IAxoObjectInstance o : getModel().getObjectInstances()) {
            if ((o.getX() == x) && (o.getY() == y)) {
                return o;
            }
        }
        return null;
    }

    AxoObjectInstanceAbstract add_unlinked_objectinstance(IAxoObjectInstance o) {
        IAxoObject t = o.resolveType(getModel().getCurrentWorkingDirectory());
        AxoObjectInstanceAbstract linked_object_instance
                = AxoObjectInstanceFactory.createView(t, getModel(), o.getInstanceName(), o.getLocation());
        addUndoableElementToList(PatchModel.PATCH_OBJECTINSTANCES, linked_object_instance);
        linked_object_instance.applyValues(o);
        linked_object_instance.setDocumentRoot(getDocumentRoot());
        return linked_object_instance;
    }

    /**
     * Paste XML string containing patch into patch.
     *
     * @param string :
     * @param pos : position in patch
     * @param restoreConnectionsToExternalOutlets : Only false is implemented!
     */
    public void paste(String string, Point pos, boolean restoreConnectionsToExternalOutlets) {
        if (string.isEmpty()) {
            return;
        }
        Strategy strategy = new AnnotationStrategy();
        Serializer serializer = new Persister(strategy);
        try {
            PatchModel p = serializer.read(PatchModel.class, string);
            Map<String, String> dict = new HashMap<>();
            /* // TODO: fix modulations
             ArrayList<IAxoObjectInstance> obj2 = new ArrayList<>(p.objectinstances);
             for (AxoObjectInstanceAbstract o : obj2) {
             o.patchModel = getModel();
             AxoObjectAbstract obj = o.resolveType();
             if (o instanceof AxoObjectInstance)
             getModel().applyType((AxoObjectInstance)o, obj);
             if (obj != null) {
             Modulator[] m = obj.getPatchModulators();
             if (m != null) {
             if (getModel().Modulators == null) {
             getModel().Modulators = new ArrayList<Modulator>();
             }
             for (Modulator mm : m) {
             mm.objinst = o;
             getModel().Modulators.add(mm);
             }
             }
             } else {
             //o.patch = this;
             p.objectinstances.remove(o);
             AxoObjectZombie z = new AxoObjectZombie();
             AxoObjectInstanceZombie zombie = new AxoObjectInstanceZombie(z.createController(null, null), getModel(), o.getInstanceName(), new Point(o.getX(), o.getY()));
             zombie.patchModel = getModel();
             zombie.typeName = o.typeName;
             p.objectinstances.add(zombie);
             }
             }
             */

            selectNone();

            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            for (IAxoObjectInstance o : p.objectinstances) {
                String original_name = o.getInstanceName();
                if (original_name != null) {
                    String new_name = original_name;
                    String ss[] = new_name.split("_");
                    boolean hasNumeralSuffix = false;
                    try {
                        if ((ss.length > 1) && (Integer.toString(Integer.parseInt(ss[ss.length - 1]))).equals(ss[ss.length - 1])) {
                            hasNumeralSuffix = true;
                        }
                    } catch (NumberFormatException e) {
                    }
                    if (hasNumeralSuffix) {
                        int n = Integer.parseInt(ss[ss.length - 1]) + 1;
                        String bs = original_name.substring(0, original_name.length() - ss[ss.length - 1].length());
                        while (getModel().findObjectInstance(new_name) != null) {
                            new_name = bs + n++;
                        }
                        while (dict.containsKey(new_name)) {
                            new_name = bs + n++;
                        }
                    } else {
                        int numeralSuffix = 1;
                        while ((getModel().findObjectInstance(new_name) != null) || (dict.containsKey(new_name))) {
                            new_name = original_name + "_" + numeralSuffix;
                            numeralSuffix++;
                        }
                    }
                    if (!new_name.equals(original_name)) {
                        o.setInstanceName(new_name);
                    }
                    dict.put(original_name, new_name);
                }
                if (o.getX() < minX) {
                    minX = o.getX();
                }
                if (o.getY() < minY) {
                    minY = o.getY();
                }
                int newposx = o.getX();
                int newposy = o.getY();

                if (pos != null) {
                    // paste at cursor position, with delta snapped to grid
                    newposx += Constants.X_GRID * ((pos.x - minX + Constants.X_GRID / 2) / Constants.X_GRID);
                    newposy += Constants.Y_GRID * ((pos.y - minY + Constants.Y_GRID / 2) / Constants.Y_GRID);
                }
                while (getObjectAtLocation(newposx, newposy) != null) {
                    newposx += Constants.X_GRID;
                    newposy += Constants.Y_GRID;
                }
                o.setLocation(new Point(newposx, newposy));
                AxoObjectInstanceAbstract oLinked = add_unlinked_objectinstance(o);
                oLinked.getController().changeSelected(true);
            }

            for (Net n : p.nets) {
                InletInstance connectedInlet = null;
                OutletInstance connectedOutlet = null;
                ArrayList<OutletInstance> source2 = new ArrayList<>();
                for (OutletInstance o : n.getSources()) {
                    String objname = o.getObjname();
                    String outletname = o.getName();
                    if ((objname != null) && (outletname != null)) {
                        String on2 = dict.get(objname);
                        if (on2 != null) {
                            OutletInstance i = new OutletInstance(on2, outletname);
                            source2.add(i);
                        } else if (restoreConnectionsToExternalOutlets) {
                            /* Review when implementing restoreConnectionsToExternalOutlets: */
                            // this is untested and probably faulty.
                            IAxoObjectInstance obj = getModel().findObjectInstance(objname);
                            if ((obj != null) && (connectedOutlet == null)) {
                                OutletInstance oi = obj.findOutletInstance(outletname);
                                if (oi != null) {
                                    connectedOutlet = oi;
                                }
                            }
                        }
                    }
                }
                n.setSources(source2);

                ArrayList<InletInstance> dest2 = new ArrayList<>();
                for (InletInstance o : n.getDestinations()) {
                    String objname = o.getObjname();
                    String inletname = o.getName();
                    if ((objname != null) && (inletname != null)) {
                        String on2 = dict.get(objname);
                        if (on2 != null) {
                            InletInstance i = new InletInstance(on2, inletname);
                            dest2.add(i);
                        }
                    }
                }
                n.setDestinations(dest2);

                if (n.getSources().size() + n.getDestinations().size() > 1) {
                    if ((connectedInlet == null) && (connectedOutlet == null)) {
                        Net n2 = new Net(getModel(), n.getSources().toArray(new OutletInstance[0]), n.getDestinations().toArray(new InletInstance[0]));
                        addUndoableElementToList(PatchModel.PATCH_NETS, n2);
                    }
//                    /* Review when implementing restoreConnectionsToExternalOutlets: */
//                    else if (connectedInlet != null) {
//                        for (InletInstance o : n.getDestinations()) {
//                            InletInstance o2 = getInletByReference(o.getObjname(), o.getInletname());
//                            if ((o2 != null) && (o2 != connectedInlet)) {
//                                addConnection(connectedInlet, o2);
//                            }
//                        }
//                        for (OutletInstance o : n.getSources()) {
//                            OutletInstance o2 = getOutletByReference(o.getObjname(), o.getOutletname());
//                            if (o2 != null) {
//                                addConnection(connectedInlet, o2);
//                            }
//                        }
//                    } else if (connectedOutlet != null) {
//                        for (InletInstance o : n.getDestinations()) {
//                            InletInstance o2 = getInletByReference(o.getObjname(), o.getInletname());
//                            if (o2 != null) {
//                                addConnection(o2, connectedOutlet);
//                            }
//                        }
//                        netControllers.add(n);
//                    }
                }
            }
        } catch (javax.xml.stream.XMLStreamException ex) {
            // silence
        } catch (Exception ex) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Deprecated // TODO: needs to ask PatchView
    public Point getViewLocationOnScreen() {
        // fake it for now
        return new Point(100, 100); //patchView.getLocationOnScreen();
    }

    public void checkCoherency() {
        for (Net n : getModel().getNets()) {
            for (InletInstance i : n.getDestinations()) {
                IAxoObjectInstance o = i.getParent();
                assert (o.getInletInstances().contains(i));
                assert (getModel().getObjectInstances().contains(o));
                assert (getNetFromIolet(i) == n);
            }
            for (OutletInstance i : n.getSources()) {
                IAxoObjectInstance o = i.getParent();
                assert (o.getOutletInstances().contains(i));
                assert (getModel().getObjectInstances().contains(o));
                assert (getNetFromIolet(i) == n);
            }
        }
        for (IAxoObjectInstance o : getModel().getObjectInstances()) {
            assert (o.getParent() == getModel());
            for (ParameterInstance p : o.getParameterInstances()) {
                assert (p.getObjectInstance() == o);
            }
            for (InletInstance p : o.getInletInstances()) {
                assert (p.getParent() == o);
            }
            for (OutletInstance p : o.getOutletInstances()) {
                assert (p.getParent() == o);
            }
        }
    }

    public void disconnect(IAxoObjectInstance o) {
        for (InletInstance i : o.getInletInstances()) {
            disconnect(i);
        }
        for (OutletInstance i : o.getOutletInstances()) {
            disconnect(i);
        }
    }

    public void convertToEmbeddedObj(IAxoObjectInstance obj) {
        changeObjectInstanceType(obj, new AxoObjectPatcherObject());
    }

    public void convertToPatchPatcher(IAxoObjectInstance obj) {
        changeObjectInstanceType(obj, new AxoObjectPatcher());
    }

    public AxoObjectInstanceAbstract changeObjectInstanceType(IAxoObjectInstance obj, IAxoObject objType) {
        try {
            AxoObjectInstanceAbstract newObj;
            if ((objType instanceof AxoObjectPatcher)
                    && (obj.getDModel() instanceof AxoObjectFromPatch)) {
                // ConvertToPatchPatcher
                AxoObjectPatcher po = (AxoObjectPatcher) objType;
                Strategy strategy = new AnnotationStrategy();
                Serializer serializer = new Persister(strategy);
                AxoObjectFromPatch ofp = (AxoObjectFromPatch) obj.getDModel();
                PatchModel pm = serializer.read(PatchModel.class, new File(ofp.getPath()));
                AxoObjectInstancePatcher newObj1 = new AxoObjectInstancePatcher(po, getModel(), obj.getInstanceName(), obj.getLocation());
                newObj1.setSubPatchModel(pm);
                newObj = newObj1;
            } else if (objType instanceof AxoObjectPatcherObject) {
                // ConvertToEmbeddedObj
                // clone by serialization/deserialization...
                ByteArrayOutputStream os = new ByteArrayOutputStream(2048);
                Strategy strategy = new AnnotationStrategy();
                Serializer serializer = new Persister(strategy);
                serializer.write(obj.getDModel(), os);
                ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
                AxoObjectPatcherObject of = serializer.read(AxoObjectPatcherObject.class, is);
                newObj = new AxoObjectInstancePatcherObject(of, getModel(), obj.getInstanceName(), obj.getLocation());
            } else {
                newObj = AxoObjectInstanceFactory.createView(objType, getModel(), obj.getInstanceName(), obj.getLocation());
            }
            newObj.applyValues(obj);

            addUndoableElementToList(PatchModel.PATCH_OBJECTINSTANCES, newObj);

            for (InletInstance i : obj.getInletInstances()) {
                Net n = getNetFromIolet(i);
                if (n != null) {
                    for (InletInstance i2 : newObj.getInletInstances()) {
                        if (i2.getName().equals(i.getName())) {
                            n.getController().connectInlet(i2);
                            break;
                        }
                    }
                }
            }
            for (OutletInstance i : obj.getOutletInstances()) {
                Net n = getNetFromIolet(i);
                if (n != null) {
                    for (OutletInstance i2 : newObj.getOutletInstances()) {
                        if (i2.getName().equals(i.getName())) {
                            n.getController().connectOutlet(i2);
                            break;
                        }
                    }
                }
            }
            disconnect(obj);
            removeUndoableElementFromList(PatchModel.PATCH_OBJECTINSTANCES, obj);
            return newObj;
        } catch (Exception ex) {
            Logger.getLogger(PatchController.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public boolean isLocked() {
        return getModel().getLocked();
    }

    public void setLocked(boolean locked) {
        setModelProperty(PatchModel.PATCH_LOCKED, (Boolean) locked);
    }

    public void setDspLoad(int DSPLoad) {
        setModelProperty(PatchModel.PATCH_DSPLOAD, (Integer) DSPLoad);
    }

    // ------------- new objectinstances MVC stuff

    public Net getNetFromIolet(IoletInstance il) {
        for (Net n : getModel().getNets()) {
            if (il.isSource()) {
                for (OutletInstance d : n.getSources()) {
                    if (d == il) {
                        return n;
                    }
                }
            }
            else {
                for (InletInstance d : n.getDestinations()) {
                    if (d == il) {
                        return n;
                    }
                }
            }
        }
        return null;
    }

    public Net addConnection(InletInstance il, OutletInstance ol) {
        if (il.getParent().getParent() != getModel()) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: different patch");
            return null;
        }
        if (ol.getParent().getParent() != getModel()) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: different patch");
            return null;
        }
        Net n1, n2;
        n1 = getNetFromIolet(il);
        n2 = getNetFromIolet(ol);
        if ((n1 == null) && (n2 == null)) {
            Net n = new Net(getModel(), new OutletInstance[]{ol}, new InletInstance[]{il});
            addUndoableElementToList(PatchModel.PATCH_NETS, n);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: new net added");
            return n;
        } else if (n1 == n2) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can''t connect: already connected");
            return null;
        } else if ((n1 != null) && (n2 == null)) {
            if (n1.getSources().isEmpty()) {
                Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: adding outlet to inlet net");
                n1.getController().connectOutlet(ol);
                return n1;
            } else {
                disconnect(il);
                Net n = new Net(getModel(), new OutletInstance[]{ol}, new InletInstance[]{il});
                addUndoableElementToList(PatchModel.PATCH_NETS, n);
                Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: new net added");
                return n;
            }
        } else if ((n1 == null) && (n2 != null)) {
            n2.getController().connectInlet(il);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: add additional outlet");
            return n2;
        } else if ((n1 != null) && (n2 != null)) {
            // inlet already has connect, and outlet has another
            // replace
            disconnect(il);
            n2.getController().connectInlet(il);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: replace inlet with existing net");
            return n2;
        }
        return null;
    }

    public Net addConnection(InletInstance il, InletInstance ol) {
        if (il == ol) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: same inlet");
            return null;
        }
        if (il.getParent().getParent() != getModel()) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: different patch");
            return null;
        }
        if (ol.getParent().getParent() != getModel()) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: different patch");
            return null;
        }
        Net n1, n2;
        n1 = getNetFromIolet(il);
        n2 = getNetFromIolet(ol);
        if ((n1 == null) && (n2 == null)) {
            Net n = new Net(getModel(), new OutletInstance[]{}, new InletInstance[]{il, ol});
            addUndoableElementToList(PatchModel.PATCH_NETS, n);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: new net added");
            return n;
        } else if (n1 == n2) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can''t connect: already connected");
        } else if ((n1 != null) && (n2 == null)) {
            n1.getController().connectInlet(ol);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: inlet added");
            return n1;
        } else if ((n1 == null) && (n2 != null)) {
            n2.getController().connectInlet(il);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: inlet added");
            return n2;
        } else if ((n1 != null) && (n2 != null)) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: both inlets included in net");
            return null;
        }
        return null;
    }

    /**
     * Disconnect inlet, remove net if there are no other connections left
     *
     * @param inlet
     * @return true if successful
     */
    public boolean disconnect(InletInstance inlet) {
        Net n = getNetFromIolet(inlet);
        if (n == null) {
            return false;
        }
        if ((n.getDestinations().size() + n.getSources().size() == 2)) {
            delete(n);
        } else {
            n.getController().disconnect(inlet);
        }
        return true;
    }

    /**
     * Disconnect outlet, remove net if there are no other connections left
     *
     * @param outlet
     * @return true if successful
     */
    public boolean disconnect(OutletInstance outlet) {
        Net n = getNetFromIolet(outlet);
        if (n == null) {
            return false;
        }
        if ((n.getDestinations().size() + n.getSources().size() == 2)) {
            delete(n);
        } else {
            n.getController().disconnect(outlet);
        }
        return true;
    }

    public void delete(Net n) {
        for (InletInstance io : n.getDestinations()) {
            io.getController().changeConnected(false);
        }
        for (OutletInstance oi : n.getSources()) {
            oi.getController().changeConnected(false);
        }
        removeUndoableElementFromList(PatchModel.PATCH_NETS, n);
    }

    @Deprecated // no longer in use?
    void exportAxoObj(File f1) {
        String fnNoExtension = f1.getName().substring(0, f1.getName().lastIndexOf(".axo"));

        getModel().sortByPosition();
        // cheating here by creating a new controller...
        PatchViewCodegen codegen = new PatchViewCodegen(model);
        AxoObject ao = codegen.generateAxoObj(new AxoObject());
        ao.setDescription(getModel().getFileNamePath());
        ao.id = fnNoExtension;

        AxoObjectFile aof = new AxoObjectFile();
        aof.objs.add(ao);
        Serializer serializer = new Persister();
        try {
            serializer.write(aof, f1);
        } catch (Exception ex) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, null, ex);
        }
        Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "Export obj complete");
    }

    public void selectNone() {
        for (IAxoObjectInstance o : getModel().getObjectInstances()) {
            o.getController().changeSelected(false);
        }
    }

    public void selectAll() {
        for (IAxoObjectInstance o : getModel().getObjectInstances()) {
            o.getController().changeSelected(true);
        }
    }

    public List<IAxoObjectInstance> getSelectedObjects() { // TODO: move method to PatchModel
        ArrayList<IAxoObjectInstance> selected = new ArrayList<>();
        for (IAxoObjectInstance o : getModel().getObjectInstances()) {
            if (o.getSelected()) {
                selected.add(o);
            }
        }
        return selected;
    }

    public boolean promoteToOverloadedObj(IAxoObjectInstance axoObjectInstance) {
        if (axoObjectInstance.getDModel() instanceof AxoObjectFromPatch) {
            return false;
        }
        if (axoObjectInstance.getDModel() instanceof AxoObjectPatcher) {
            return false;
        }
        if (axoObjectInstance.getDModel() instanceof AxoObjectPatcherObject) {
            return false;
        }
        String id = axoObjectInstance.getDModel().getId();
        List<IAxoObject> candidates = AxoObjects.getAxoObjects().getAxoObjectFromName(id, axoObjectInstance.getParent().getCurrentWorkingDirectory());
        if (candidates == null) {
            return false;
        }
        if (candidates.isEmpty()) {
            Logger.getLogger(AxoObjectInstance.class.getName()).log(Level.SEVERE, "could not resolve any candidates {0}", id);
        }
        if (candidates.size() == 1) {
            return false;
        }
        int[] ranking;
        ranking = new int[candidates.size()];
        for (InletInstance j : axoObjectInstance.getInletInstances()) {
            Net n = getNetFromIolet(j);
            if (n == null) {
                continue;
            }
            DataType d = n.getDataType();
            if (d == null) {
                continue;
            }
            String name = j.getDModel().getName();
            for (int i = 0; i < candidates.size(); i++) {
                IAxoObject o = candidates.get(i);
                Inlet i2 = null;
                for (Inlet i3 : o.getInlets()) {
                    if (name.equals(i3.getName())) {
                        i2 = i3;
                        break;
                    }
                }
                if (i2 == null) {
                    continue;
                }
                if (i2.getDataType().equals(d)) {
                    ranking[i] += 10;
                } else if (d.isConvertableToType(i2.getDataType())) {
                    ranking[i] += 2;
                }
            }
        }
        int max = -1;
        int maxi = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (ranking[i] > max) {
                max = ranking[i];
                maxi = i;
            }
        }
        IAxoObject selected = candidates.get(maxi);
        int rindex = candidates.indexOf(axoObjectInstance.getDModel());
        if (rindex >= 0) {
            if (ranking[rindex] == max) {
                selected = axoObjectInstance.getDModel();
            }
        }
        if (selected == null) {
            //Logger.getLogger(AxoObjectInstance.class.getName()).log(Level.INFO,"no promotion to null" + this + " to " + selected);
            return false;
        }
        if (selected != axoObjectInstance.getDModel()) {
            Logger.getLogger(AxoObjectInstance.class.getName()).log(Level.FINE, "promoting {0} to {1}", new Object[]{axoObjectInstance, selected});
            changeObjectInstanceType(axoObjectInstance, selected);
            return true;
        }
        return false;
    }

    // broken: should move to controller...
    /*
    public AxoObjectInstanceAbstract ChangeObjectInstanceType1(AxoObjectInstanceAbstract oldObject, AxoObjectAbstract newObjectType) {
    if ((oldObject instanceof AxoObjectInstancePatcher) && (newObjectType instanceof AxoObjectPatcher)) {
    return oldObject;
    } else if ((oldObject instanceof AxoObjectInstancePatcherObject) && (newObjectType instanceof AxoObjectPatcherObject)) {
    return oldObject;
    } else if (oldObject instanceof AxoObjectInstance) {
    String n = oldObject.getInstanceName();
    oldObject.setInstanceName(n + Constants.TEMP_OBJECT_SUFFIX);
    AxoObjectInstanceAbstract newObject = addObjectInstance(newObjectType, new Point(oldObject.getX(), oldObject.getY()));
     if (newObject instanceof AxoObjectInstance) {
    transferState((AxoObjectInstance) oldObject, (AxoObjectInstance) newObject);
    }
    return newObject;
    } else if (oldObject instanceof AxoObjectInstanceZombie) {
    AxoObjectInstanceAbstract newObject = addObjectInstance(newObjectType, new Point(oldObject.getX(), oldObject.getY()));
     if ((newObject instanceof AxoObjectInstance)) {
    transferObjectConnections((AxoObjectInstanceZombie) oldObject, (AxoObjectInstance) newObject);
    }
    return newObject;
    }
    return oldObject;
    }
    public AxoObjectInstanceAbstract ChangeObjectInstanceType(AxoObjectInstanceAbstract oldObject, AxoObjectAbstract newObjectType) {
    AxoObjectInstanceAbstract newObject = ChangeObjectInstanceType1(oldObject, newObjectType);
    if (newObject != oldObject) {
    //            delete(oldObject);
    setDirty();
    }
    return newObject;
    }
     */
    /**
     *
     * @param initial If true, only objects restored from object name reference
     * (not UUID) will promote to a variant with the same name.
     */
    public boolean promoteOverloading(boolean initial) {
        PatchModel patchModel = getModel();
        patchModel.refreshIndexes();
        Set<String> ProcessedInstances = new HashSet<>();
        boolean p = true;
        boolean promotionOccured = false;
        while (p && !(ProcessedInstances.size() == patchModel.objectinstances.size())) {
            p = false;
            for (IAxoObjectInstance o : patchModel.objectinstances) {
                if (!ProcessedInstances.contains(o.getInstanceName())) {
                    ProcessedInstances.add(o.getInstanceName());
                    if (!initial || o.isTypeWasAmbiguous()) {
                        promotionOccured |= promoteToOverloadedObj(o);
                    }
                    p = true;
                    break;
                }
            }
        }
        if (!(ProcessedInstances.size() == patchModel.objectinstances.size())) {
            for (IAxoObjectInstance o : patchModel.objectinstances) {
                if (!ProcessedInstances.contains(o.getInstanceName())) {
                    Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, "PromoteOverloading : fault in {0}", o.getInstanceName());
                }
            }
        }
        return promotionOccured;
    }

}
