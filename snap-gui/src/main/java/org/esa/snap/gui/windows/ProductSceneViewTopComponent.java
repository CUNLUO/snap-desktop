/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esa.snap.gui.windows;

import com.bc.ceres.swing.selection.Selection;
import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.ceres.swing.selection.SelectionChangeListener;
import org.esa.beam.framework.datamodel.ProductNode;
import org.esa.beam.framework.datamodel.ProductNodeEvent;
import org.esa.beam.framework.datamodel.ProductNodeListenerAdapter;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.snap.gui.util.DocumentTopComponent;
import org.esa.snap.gui.util.WindowUtilities;
import org.openide.awt.UndoRedo;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.windows.WindowManager;

import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * A document window which displays images
 *
 * @author Norman
 */
public class ProductSceneViewTopComponent extends DocumentTopComponent<ProductNode>
        implements UndoRedo.Provider, SelectionChangeListener {

    private static final Logger LOG = Logger.getLogger(ProductSceneViewTopComponent.class.getName());

    private final ProductSceneView view;
    private UndoRedo undoRedo;
    private final ProductNodeListenerAdapter nodeRenameHandler;
    private Selection selection;

    public ProductSceneViewTopComponent(ProductSceneView view, UndoRedo undoRedo) {
        super(view.getRaster());
        this.view = view;
        this.undoRedo = undoRedo;
        this.nodeRenameHandler = new NodeRenameHandler();
        this.selection = Selection.EMPTY;
        initComponents();
        RasterDataNode raster = view.getRaster();
        setName(raster.getName());
        setDisplayName(WindowUtilities.getUniqueTitle(raster.getName(), ProductSceneViewTopComponent.class));
        setToolTipText(raster.getProduct().getName() + " - " + raster.getName());
        setIcon(ImageUtilities.loadImage("org/esa/snap/gui/icons/RsBandAsSwath16.gif"));
/*
        // checkme - this is ugly and not wanted (nf), the node will either passed in or we'll have
        // a central node factory, e.g. via an ExtensionObject
        Node node = null;
        try {
            if (raster instanceof Mask) {
                node = new MNode((Mask) raster, undoRedo);
            } else if (raster instanceof Band) {
                node = new BNode((Band) raster, undoRedo);
            } else if (raster instanceof TiePointGrid) {
                node = new TPGNode((TiePointGrid) raster, undoRedo);
            }
        } catch (IntrospectionException e) {
            Exceptions.printStackTrace(e);
        }
        if (node != null) {
            setActivatedNodes(new Node[]{node});
        }
*/
    }

    @Override
    public UndoRedo getUndoRedo() {
        return undoRedo;
    }

    public ProductSceneView getView() {
        return view;
    }

    @Override
    public void selectionChanged(SelectionChangeEvent event) {
        setSelection(event.getSelection());
    }

    @Override
    public void selectionContextChanged(SelectionChangeEvent event) {
    }

    @Override
    protected void componentOpened() {
        getDocument().getProduct().addProductNodeListener(nodeRenameHandler);
    }

    @Override
    protected void componentClosed() {
        getDocument().getProduct().removeProductNodeListener(nodeRenameHandler);
    }

    @Override
    protected void componentActivated() {
        getDynamicContent().add(view);
        setSelection(view.getFigureEditor().getSelectionContext().getSelection());
        view.getFigureEditor().getSelectionContext().addSelectionChangeListener(this);
    }

    @Override
    protected void componentDeactivated() {
        view.getFigureEditor().getSelectionContext().removeSelectionChangeListener(this);
        setSelection(Selection.EMPTY);
        getDynamicContent().remove(view);
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        add(view, BorderLayout.CENTER);
    }

    private void setSelection(Selection newSelection) {
        Selection oldSelection = this.selection;
        getDynamicContent().remove(oldSelection);
        if (!newSelection.isEmpty()) {
            this.selection = newSelection.clone();
            getDynamicContent().add(newSelection);
        }

        updateActionMap(newSelection);
    }

    private void updateActionMap(Selection newSelection) {
        ActionMap actionMap = getActionMap();
        actionMap.put("select-all", new SelectAllAction());

        actionMap.put(DefaultEditorKit.pasteAction, new PasteAction());
        if (!newSelection.isEmpty()) {
            actionMap.put(DefaultEditorKit.cutAction, new CutAction());
            actionMap.put(DefaultEditorKit.copyAction, new CopyAction());
            actionMap.put("delete", new DeleteAction());
            actionMap.put("deselect-all", new DeselectAllAction());
        } else {
            actionMap.remove(DefaultEditorKit.cutAction);
            actionMap.remove(DefaultEditorKit.copyAction);
            actionMap.remove("delete");
            actionMap.remove("deselect-all");
        }
        getDynamicContent().remove(actionMap);
        getDynamicContent().add(actionMap);
    }

    private class CutAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            Selection selection = getLookup().lookup(Selection.class);
            if (selection != null && !selection.isEmpty()) {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                Transferable transferable = view.getFigureEditor().getFigureSelection().createTransferable(false);
                clipboard.setContents(transferable, selection);
                view.getFigureEditor().deleteSelection();
                //JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), "Cut: " + transferable);
            }
        }
    }

    private class CopyAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            Selection selection = getLookup().lookup(Selection.class);
            if (selection != null && !selection.isEmpty()) {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                // todo - when we copy we actually don't clone the SimpleFeature. Then, if we paste, two figures refer
                // to the same SimpleFeature.
                Transferable transferable = view.getFigureEditor().getFigureSelection().createTransferable(true);
                clipboard.setContents(transferable, selection);
                //JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), "Copy: " + transferable);
            }
        }
    }

    private class PasteAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(view);
            //JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), "Paste: " + contents);

            try {
                view.getSelectionContext().insert(contents);
            } catch (IOException | UnsupportedFlavorException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private class DeleteAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            view.getFigureEditor().deleteSelection();
        }
    }

    private class SelectAllAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            view.getFigureEditor().selectAll();
        }
    }

    private class DeselectAllAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            view.getFigureEditor().setSelection(Selection.EMPTY);
        }
    }

    private class NodeRenameHandler extends ProductNodeListenerAdapter {
        @Override
        public void nodeChanged(final ProductNodeEvent event) {
            if (event.getSourceNode() == getDocument() &&
                    event.getPropertyName().equalsIgnoreCase(ProductNode.PROPERTY_NAME_NAME)) {
                setDisplayName(getDocument().getName());
            }
        }
    }
}