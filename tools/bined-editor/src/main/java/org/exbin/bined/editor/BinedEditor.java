/*
 * Copyright (C) ExBin Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.exbin.bined.editor;

import java.awt.Dimension;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.exbin.framework.XBBaseApplication;
import org.exbin.framework.api.Preferences;
import org.exbin.framework.api.XBApplicationModuleRepository;
import org.exbin.framework.bined.BinedModule;
import org.exbin.framework.bined.preferences.BinaryAppearancePreferences;
import org.exbin.framework.gui.about.api.GuiAboutModuleApi;
import org.exbin.framework.gui.editor.api.GuiEditorModuleApi;
import org.exbin.framework.gui.file.api.GuiFileModuleApi;
import org.exbin.framework.gui.frame.api.ApplicationFrameHandler;
import org.exbin.framework.gui.frame.api.GuiFrameModuleApi;
import org.exbin.framework.gui.link.api.GuiLinkModuleApi;
import org.exbin.framework.gui.action.api.GuiActionModuleApi;
import org.exbin.framework.gui.options.api.GuiOptionsModuleApi;
import org.exbin.framework.gui.undo.api.GuiUndoModuleApi;
import org.exbin.framework.gui.update.api.GuiUpdateModuleApi;
import org.exbin.framework.gui.utils.LanguageUtils;
import org.exbin.framework.gui.editor.api.EditorProvider;
import org.exbin.framework.gui.editor.api.EditorProviderVariant;
import org.exbin.framework.gui.undo.api.UndoFileHandler;

/**
 * The main class of the BinEd Hexadecimal Editor application.
 *
 * @version 0.2.1 2021/10/14
 * @author ExBin Project (http://exbin.org)
 */
@ParametersAreNonnullByDefault
public class BinedEditor {

    private static final String BINARY_PLUGIN_ID = "binary";

    private static boolean verboseMode = false;
    private static boolean devMode = false;
    private static ResourceBundle bundle;

    /**
     * Main method launching the application.
     *
     * @param args arguments
     */
    public static void main(String[] args) {
        try {
            bundle = LanguageUtils.getResourceBundleByClass(BinedEditor.class);
            // Parameters processing
            Options opt = new Options();
            opt.addOption("h", "help", false, bundle.getString("cl_option_help"));
            opt.addOption("v", false, bundle.getString("cl_option_verbose"));
            opt.addOption("dev", false, bundle.getString("cl_option_dev"));
            BasicParser parser = new BasicParser();
            CommandLine cl = parser.parse(opt, args);
            if (cl.hasOption('h')) {
                HelpFormatter f = new HelpFormatter();
                f.printHelp(bundle.getString("cl_syntax"), opt);
            } else {
                verboseMode = cl.hasOption("v");
                devMode = cl.hasOption("dev");

                XBBaseApplication app = new XBBaseApplication();
                Preferences preferences = app.createPreferences(BinedEditor.class);
                app.setAppBundle(bundle, LanguageUtils.getResourceBaseNameBundleByClass(BinedEditor.class));
                BinaryAppearancePreferences binaryAppearanceParameters = new BinaryAppearancePreferences(preferences);
                boolean multiFileMode = binaryAppearanceParameters.isMultiFileMode();

                XBApplicationModuleRepository moduleRepository = app.getModuleRepository();
                moduleRepository.addClassPathModules();
                moduleRepository.addModulesFromManifest(BinedEditor.class);
                moduleRepository.loadModulesFromPath(new File("plugins").toURI());
                moduleRepository.initModules();
                app.init();

                final GuiFrameModuleApi frameModule = moduleRepository.getModuleByInterface(GuiFrameModuleApi.class);
                GuiEditorModuleApi editorModule = moduleRepository.getModuleByInterface(GuiEditorModuleApi.class);
                GuiActionModuleApi actionModule = moduleRepository.getModuleByInterface(GuiActionModuleApi.class);
                GuiAboutModuleApi aboutModule = moduleRepository.getModuleByInterface(GuiAboutModuleApi.class);
                GuiLinkModuleApi linkModule = moduleRepository.getModuleByInterface(GuiLinkModuleApi.class);
                GuiUndoModuleApi undoModule = moduleRepository.getModuleByInterface(GuiUndoModuleApi.class);
                GuiFileModuleApi fileModule = moduleRepository.getModuleByInterface(GuiFileModuleApi.class);
                GuiOptionsModuleApi optionsModule = moduleRepository.getModuleByInterface(GuiOptionsModuleApi.class);
                GuiUpdateModuleApi updateModule = moduleRepository.getModuleByInterface(GuiUpdateModuleApi.class);

                BinedModule binedModule = moduleRepository.getModuleByInterface(BinedModule.class);
                binedModule.initEditorProvider(multiFileMode ? EditorProviderVariant.MULTI : EditorProviderVariant.SINGLE);
                EditorProvider editorProvider = binedModule.getEditorProvider();
                editorModule.registerEditor(BINARY_PLUGIN_ID, editorProvider);

                frameModule.createMainMenu();
                try {
                    updateModule.setUpdateUrl(new URL(bundle.getString("update_url")));
                    updateModule.setUpdateDownloadUrl(new URL(bundle.getString("update_download_url")));
                } catch (MalformedURLException ex) {
                    Logger.getLogger(BinedEditor.class.getName()).log(Level.SEVERE, null, ex);
                }
                updateModule.registerDefaultMenuItem();
                aboutModule.registerDefaultMenuItem();
                try {
                    linkModule.setOnlineHelpUrl(new URL(bundle.getString("online_help_url")));
                } catch (MalformedURLException ex) {
                    Logger.getLogger(BinedEditor.class.getName()).log(Level.SEVERE, null, ex);
                }
                linkModule.registerOnlineHelpMenu();

                frameModule.registerExitAction();
                frameModule.registerBarsVisibilityActions();

                fileModule.registerMenuFileHandlingActions();
                if (multiFileMode) {
                    editorModule.registerMenuFileCloseActions();
                }

                fileModule.registerToolBarFileHandlingActions();
                fileModule.registerRecenFilesMenuActions();
                fileModule.registerCloseListener();

                undoModule.registerMainMenu();
                undoModule.registerMainToolBar();
                undoModule.registerUndoManagerInMainMenu();

                // Register clipboard editing actions
                actionModule.registerClipboardTextActions();
                actionModule.registerMenuClipboardActions();
                actionModule.registerToolBarClipboardActions();

                optionsModule.registerMenuAction();

                binedModule.registerEditFindMenuActions();
                binedModule.registerCodeTypeToolBarActions();
                binedModule.registerShowUnprintablesToolBarActions();
//                binedModule.registerEditFindToolBarActions();
                binedModule.registerViewUnprintablesMenuActions();
                binedModule.registerViewValuesPanelMenuActions();
                binedModule.registerToolsOptionsMenuActions();
                binedModule.registerClipboardCodeActions();
                binedModule.registerOptionsMenuPanels();
                binedModule.registerGoToPosition();
                binedModule.registerInsertDataAction();
                binedModule.registerPropertiesMenu();
                // TODO binedModule.registerPrintMenu();
                binedModule.registerViewModeMenu();
                binedModule.registerCodeTypeMenu();
                binedModule.registerPositionCodeTypeMenu();
                binedModule.registerHexCharactersCaseHandlerMenu();
                binedModule.registerLayoutMenu();

                final ApplicationFrameHandler frameHandler = frameModule.getFrameHandler();
//                UndoHandlerWrapper undoHandlerWrapper = new UndoHandlerWrapper();

                undoModule.setUndoHandler(((UndoFileHandler) editorProvider).getUndoHandler());
                editorModule.registerUndoHandler();

                binedModule.registerStatusBar();
                binedModule.registerOptionsPanels();
                binedModule.getBinaryStatusPanel();
                updateModule.registerOptionsPanels();

                binedModule.loadFromPreferences(preferences);

                frameModule.addExitListener((ApplicationFrameHandler afh) -> {
                    frameModule.saveFramePosition();
                    return true;
                });

                frameHandler.setMainPanel(editorModule.getEditorComponent());

                frameHandler.setDefaultSize(new Dimension(600, 400));
                frameModule.loadFramePosition();
                optionsModule.initialLoadFromPreferences();
                frameHandler.show();
                updateModule.checkOnStart(frameHandler.getFrame());

                List fileArgs = cl.getArgList();
                if (fileArgs.size() > 0) {
                    String filePath = (String) fileArgs.get(0);
                    try {
                        URL url = new File(filePath).toURI().toURL();
                        fileModule.loadFromFile(url.toURI().toASCIIString());
                    } catch (MalformedURLException | URISyntaxException ex) {
                        fileModule.loadFromFile(filePath);
                    }
                }
            }
        } catch (ParseException | RuntimeException ex) {
            Logger.getLogger(BinedEditor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
