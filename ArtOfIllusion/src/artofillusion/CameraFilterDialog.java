/* Copyright (C) 2003-2005 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion;

import artofillusion.image.*;
import artofillusion.image.filter.*;
import artofillusion.math.*;
import artofillusion.object.*;
import artofillusion.ui.*;
import buoy.event.*;
import buoy.widget.*;
import java.awt.*;
import java.util.*;

/** This is dialog in which the user can edit the list of filters attached to a camera. */

public class CameraFilterDialog extends BDialog implements RenderListener
{
  private SceneCamera theCamera;
  private Scene theScene;
  private CoordinateSystem cameraCoords;
  private Vector filters;
  private ImageFilter oldFilters[];
  private Class filterClasses[];
  private BList allFiltersList, cameraFiltersList;
  private BButton addButton, deleteButton, upButton, downButton;
  private BScrollPane editorPane;
  private CustomWidget preview;
  private ComplexImage unfilteredImage;
  private Image displayImage;
  private boolean doneRendering;
  private Map savedConfiguration;
  private Thread filterThread;

  private static Renderer previewRenderer = ModellingApp.getPreferences().getTexturePreviewRenderer();
  private static HashMap rendererConfiguration = new HashMap();

  private static final int PREVIEW_WIDTH = 200;
  private static final int PREVIEW_HEIGHT = 150;

  public CameraFilterDialog(EditingWindow parent, SceneCamera camera, CoordinateSystem cameraCoords)
  {
    super(parent.getFrame(), Translate.text("Filters"), true);
    theCamera = camera;
    theScene = parent.getScene();
    this.cameraCoords = cameraCoords;
    filters = new Vector();
    oldFilters = theCamera.getImageFilters();
    for (int i = 0; i < oldFilters.length; i++)
    {
      filters.addElement(oldFilters[i]);
      oldFilters[i] = oldFilters[i].duplicate();
    }

    // Layout the major sections of the window.

    FormContainer content = new FormContainer(2, 3);
    setContent(content);
    LayoutInfo fillLayout = new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.BOTH, null, null);
    FormContainer listsPanel = new FormContainer(3, 1);
    BorderContainer previewPanel = new BorderContainer();
    RowContainer okPanel = new RowContainer();
    editorPane = new BScrollPane();
    editorPane.setPreferredViewSize(new Dimension(200, 150));
    content.add(listsPanel, 0, 0, fillLayout);
    content.add(BOutline.createBevelBorder(editorPane, false), 0, 1, new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.BOTH, new Insets(2, 2, 2, 2), null));
    content.add(previewPanel, 1, 0, 1, 2, new LayoutInfo());
    content.add(okPanel, 0, 2, 2, 1, new LayoutInfo());

    // Layout the Lists and the buttons between them.

    listsPanel.add(UIUtilities.createScrollingList(allFiltersList = new BList()), 0, 0, fillLayout);
    ColumnContainer buttonsPanel = new ColumnContainer();
    listsPanel.add(buttonsPanel, 1, 0);
    listsPanel.add(UIUtilities.createScrollingList(cameraFiltersList = new BList()), 2, 0, fillLayout);
    buttonsPanel.setDefaultLayout(new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.HORIZONTAL, new Insets(2, 2, 2, 2), null));
    buttonsPanel.add(addButton = Translate.button("add", " >>", this, "doAdd"));
    buttonsPanel.add(deleteButton = Translate.button("delete", this, "doDelete"));
    buttonsPanel.add(upButton = Translate.button("moveUp", this, "doMoveUp"));
    buttonsPanel.add(downButton = Translate.button("moveDown", this, "doMoveDown"));
    allFiltersList.addEventLink(SelectionChangedEvent.class, this, "updateComponents");
    cameraFiltersList.addEventLink(SelectionChangedEvent.class, this, "updateComponents");
    allFiltersList.setMultipleSelectionEnabled(false);
    cameraFiltersList.setMultipleSelectionEnabled(false);

    // Fill in the Lists.

    filterClasses = ModellingApp.getImageFilters();
    for (int i = 0; i < filterClasses.length; i++)
    {
      try
      {
        allFiltersList.add(((ImageFilter) filterClasses[i].newInstance()).getName());
      }
      catch (Exception ex)
      {
        ex.printStackTrace();
      }
    }
    rebuildFilterList();
    if (cameraFiltersList.getItemCount() > 0)
      cameraFiltersList.setSelected(0, true);
    ((BScrollPane) cameraFiltersList.getParent()).setPreferredViewSize(allFiltersList.getPreferredSize());

    // Create the preview panel.

    preview = new CustomWidget();
    preview.addEventLink(RepaintEvent.class, this, "paintPreview");
    preview.setPreferredSize(new Dimension(PREVIEW_WIDTH, PREVIEW_HEIGHT));
    previewPanel.add(preview, BorderContainer.CENTER);
    previewPanel.add(Translate.button("configurePreview", this, "doConfigure"), BorderContainer.SOUTH, new LayoutInfo());

    // Create the OK and Cancel buttons.

    okPanel.add(Translate.button("ok", this, "doOk"));
    okPanel.add(Translate.button("cancel", this, "doCancel"));
    updateComponents();

    // Begin rendering the preview.

    savedConfiguration = previewRenderer.getConfiguration();
    Map recordedConfig = (Map) rendererConfiguration.get(previewRenderer);
    if (recordedConfig == null)
    {
      previewRenderer.configurePreview();
      recordedConfig = previewRenderer.getConfiguration();
      rendererConfiguration.put(previewRenderer, recordedConfig);
    }
    configureRenderer(recordedConfig, previewRenderer);
    renderPreview();

    // Display the window.

    pack();
    UIUtilities.centerWindow(this);
    setVisible(true);
  }

  /** Apply a saved configure to the renderer. */

  private void configureRenderer(Map config, Renderer renderer)
  {
    if (config == null)
      return;
    Iterator options = config.entrySet().iterator();
    while (options.hasNext())
    {
      Map.Entry entry = (Map.Entry) options.next();
      renderer.setConfiguration((String) entry.getKey(), entry.getValue());
    }
  }

  /** Render the preview image. */

  private void renderPreview()
  {
    Camera cam = theCamera.createCamera(PREVIEW_WIDTH, PREVIEW_HEIGHT, cameraCoords);
    SceneCamera renderCamera = new SceneCamera() {
      public int getComponentsForFilters()
      {
        return -1; // Force the renderer to generate all possible components.
      }
    };
    renderCamera.copyObject(theCamera);
    doneRendering = false;
    previewRenderer.renderScene(theScene, cam, this, renderCamera);
  }

  /** Update the states of various components in the window. */

  private void updateComponents()
  {
    addButton.setEnabled(allFiltersList.getSelectedIndex() > -1);
    int selection = cameraFiltersList.getSelectedIndex();
    deleteButton.setEnabled(selection > -1);
    upButton.setEnabled(selection > 0);
    downButton.setEnabled(selection > -1 && selection < filters.size()-1);
    if (selection > -1)
    {
      Runnable callback = new Runnable() {
        public void run()
        {
          applyFilters();
        }
      };
      editorPane.setContent(((ImageFilter) filters.elementAt(cameraFiltersList.getSelectedIndex())).getConfigPanel(callback));
    }
    else
      editorPane.setContent(null);
    editorPane.layoutChildren();
  }

  /** Rebuild the list of filters. */

  private void rebuildFilterList()
  {
    cameraFiltersList.removeAll();
    for (int i = 0; i < filters.size(); i++)
      cameraFiltersList.add(((ImageFilter) filters.elementAt(i)).getName());
  }

  /** Save changes and close the window. */

  private void doOk()
  {
    ImageFilter filt[] = new ImageFilter [filters.size()];
    filters.copyInto(filt);
    theCamera.setImageFilters(filt);
    configureRenderer(savedConfiguration, previewRenderer);
    dispose();
  }

  /** Cancel changes and close the window. */

  private void doCancel()
  {
    ImageFilter filt[] = theCamera.getImageFilters();
    for (int i = 0; i < filt.length; i++)
      filt[i].copy(oldFilters[i]);
    configureRenderer(savedConfiguration, previewRenderer);
    dispose();
  }

  /** Add a filter to the list. */

  private void doAdd()
  {
    int sel = allFiltersList.getSelectedIndex();
    if (sel == -1)
      return;
    try
    {
      filters.addElement(filterClasses[sel].newInstance());
      rebuildFilterList();
      cameraFiltersList.setSelected(filters.size()-1, true);
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
    updateComponents();
    applyFilters();
  }

  /** Delete a filter from the list. */

  private void doDelete()
  {
    int sel = cameraFiltersList.getSelectedIndex();
    if (sel == -1)
      return;
    filters.removeElementAt(sel);
    rebuildFilterList();
    updateComponents();
    applyFilters();
  }

  /** Move the selected filter upward. */

  private void doMoveUp()
  {
    int sel = cameraFiltersList.getSelectedIndex();
    if (sel < 1)
      return;
    ImageFilter filt = (ImageFilter) filters.elementAt(sel);
    filters.removeElementAt(sel);
    filters.insertElementAt(filt, sel-1);
    rebuildFilterList();
    cameraFiltersList.setSelected(sel-1, true);
    updateComponents();
    applyFilters();
  }

  /** Move the selected filter downward. */

  private void doMoveDown()
  {
    int sel = cameraFiltersList.getSelectedIndex();
    if (sel == -1 || sel == filters.size()-1)
      return;
    ImageFilter filt = (ImageFilter) filters.elementAt(sel);
    filters.removeElementAt(sel);
    filters.insertElementAt(filt, sel+1);
    rebuildFilterList();
    cameraFiltersList.setSelected(sel+1, true);
    updateComponents();
    applyFilters();
  }

  /** Allow the user to configure the renderer used for the preview. */

  private void doConfigure()
  {
    final BorderContainer content = new BorderContainer();
    final BComboBox rendererChoice = new BComboBox();
    final Renderer renderers[] = ModellingApp.getRenderers();
    for (int i = 0; i < renderers.length; i++)
    {
      rendererChoice.add(renderers[i].getName());
      if (previewRenderer == renderers[i])
        rendererChoice.setSelectedIndex(i);
    }
    RowContainer rc = new RowContainer();
    rc.add(Translate.label("Renderer", ":"));
    rc.add(rendererChoice);
    content.add(rc, BorderContainer.NORTH, new LayoutInfo());
    content.add(previewRenderer.getConfigPanel(), BorderContainer.CENTER);
    rendererChoice.addEventLink(ValueChangedEvent.class, new Object() {
      void processEvent()
      {
        Renderer newRenderer = renderers[rendererChoice.getSelectedIndex()];
        Map recordedConfig = (Map) rendererConfiguration.get(newRenderer);
        if (recordedConfig == null)
        {
          newRenderer.configurePreview();
          recordedConfig = newRenderer.getConfiguration();
          rendererConfiguration.put(newRenderer, recordedConfig);
        }
        configureRenderer(recordedConfig, newRenderer);
        content.add(newRenderer.getConfigPanel(), BorderContainer.CENTER);
        UIUtilities.findWindow(content).pack();
      }
    });
    PanelDialog dlg = new PanelDialog(this, Translate.text("configurePreview"), content);
    if (!dlg.clickedOk())
      return;
    configureRenderer(savedConfiguration, previewRenderer);
    previewRenderer = renderers[rendererChoice.getSelectedIndex()];
    savedConfiguration = previewRenderer.getConfiguration();
    previewRenderer.recordConfiguration();
    rendererConfiguration.put(previewRenderer, previewRenderer.getConfiguration());
    renderPreview();
    preview.repaint();
  }

  /** Repaint the preview canvas. */

  private void paintPreview(RepaintEvent ev)
  {
    Graphics2D g = ev.getGraphics();
    if (displayImage != null)
      g.drawImage(displayImage, 0, 0, null);
    String message = null;
    if (!doneRendering)
      message = "Rendering Preview...";
    else if (filterThread != null && filterThread.isAlive())
      message = "Applying Filter...";
    if (message != null)
    {
      FontMetrics fm = g.getFontMetrics();
      int width = fm.stringWidth(message);
      g.setColor(Color.red);
      g.drawString(message, (PREVIEW_WIDTH-width)/2, PREVIEW_HEIGHT/2);
    }
  }

  /** Apply the filters to the image and redisplay it. */

  private synchronized void applyFilters()
  {
    preview.repaint();
    if (filterThread != null)
      filterThread.interrupt();
    filterThread = new Thread() {
      public void run()
      {
        ComplexImage img = unfilteredImage.duplicate();
        for (int i = 0; i < filters.size(); i++)
          ((ImageFilter) filters.elementAt(i)).filterImage(img, theScene, theCamera, cameraCoords);
        if (filters.size() > 0)
          img.rebuildImage();
        if (filterThread == Thread.currentThread())
          displayImage = img.getImage();
        preview.repaint();
      }
    };
    filterThread.start();
  }

  /** The renderer may call this method periodically during rendering, to notify the listener that more of the
      image is complete. */

  public void imageUpdated(Image image)
  {
    displayImage = image;
    preview.repaint();
  }

  /** The renderer may call this method periodically during rendering, to give the listener text descriptions
      of the current status of rendering. */

  public void statusChanged(String status)
  {
  }

  /** This method will be called when rendering is complete. */

  public void imageComplete(ComplexImage image)
  {
    unfilteredImage = image;
    displayImage = image.getImage();
    doneRendering = true;
    applyFilters();
  }

  /** This method will be called if rendering is canceled. */

  public void renderingCanceled()
  {
  }
}