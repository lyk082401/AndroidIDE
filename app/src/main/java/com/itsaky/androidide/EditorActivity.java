/*
 * This file is part of AndroidIDE.
 *
 *
 *
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
package com.itsaky.androidide;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.transition.Slide;
import androidx.transition.TransitionManager;

import com.blankj.utilcode.util.FileUtils;
import com.blankj.utilcode.util.IntentUtils;
import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.SizeUtils;
import com.blankj.utilcode.util.ThreadUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.itsaky.androidide.adapters.DiagnosticsAdapter;
import com.itsaky.androidide.adapters.EditorBottomSheetTabAdapter;
import com.itsaky.androidide.adapters.EditorPagerAdapter;
import com.itsaky.androidide.adapters.SearchListAdapter;
import com.itsaky.androidide.app.StudioActivity;
import com.itsaky.androidide.databinding.ActivityEditorBinding;
import com.itsaky.androidide.databinding.LayoutDiagnosticInfoBinding;
import com.itsaky.androidide.databinding.LayoutSearchProjectBinding;
import com.itsaky.androidide.fragments.EditorFragment;
import com.itsaky.androidide.fragments.FileTreeFragment;
import com.itsaky.androidide.fragments.SearchResultFragment;
import com.itsaky.androidide.fragments.sheets.OptionsListFragment;
import com.itsaky.androidide.fragments.sheets.ProgressSheet;
import com.itsaky.androidide.fragments.sheets.TextSheetFragment;
import com.itsaky.androidide.handlers.BuildServiceHandler;
import com.itsaky.androidide.handlers.FileOptionsHandler;
import com.itsaky.androidide.handlers.IDEHandler;
import com.itsaky.androidide.interfaces.DiagnosticClickListener;
import com.itsaky.androidide.interfaces.EditorActivityProvider;
import com.itsaky.androidide.lsp.IDELanguageClientImpl;
import com.itsaky.androidide.lsp.LSP;
import com.itsaky.androidide.lsp.LSPProvider;
import com.itsaky.androidide.managers.PreferenceManager;
import com.itsaky.androidide.models.DiagnosticGroup;
import com.itsaky.androidide.models.LogLine;
import com.itsaky.androidide.models.SaveResult;
import com.itsaky.androidide.models.SearchResult;
import com.itsaky.androidide.models.SheetOption;
import com.itsaky.androidide.project.AndroidProject;
import com.itsaky.androidide.project.IDEProject;
import com.itsaky.androidide.services.LogReceiver;
import com.itsaky.androidide.services.builder.IDEService;
import com.itsaky.androidide.shell.ShellServer;
import com.itsaky.androidide.tasks.TaskExecutor;
import com.itsaky.androidide.utils.DialogUtils;
import com.itsaky.androidide.utils.EditorBottomSheetBehavior;
import com.itsaky.androidide.utils.LSPUtils;
import com.itsaky.androidide.utils.Logger;
import com.itsaky.androidide.utils.RecursiveFileSearcher;
import com.itsaky.androidide.utils.Symbols;
import com.itsaky.androidide.views.MaterialBanner;
import com.itsaky.androidide.views.SymbolInputView;
import com.itsaky.inflater.ILayoutInflater;
import com.itsaky.lsp.services.IDELanguageServer;
import com.itsaky.toaster.Toaster;
import com.unnamed.b.atv.model.TreeNode;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.Contract;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.rosemoe.editor.widget.CodeEditor;
import me.piruin.quickaction.ActionItem;
import me.piruin.quickaction.QuickAction;

public class EditorActivity extends StudioActivity implements FileTreeFragment.FileActionListener,
        TabLayout.OnTabSelectedListener,
        NavigationView.OnNavigationItemSelectedListener,
        DiagnosticClickListener,
        EditorFragment.FileOpenListener,
        IDEHandler.Provider,
        EditorActivityProvider {
    
    private ActivityEditorBinding mBinding;
    private LayoutDiagnosticInfoBinding mDiagnosticInfoBinding;
    private EditorPagerAdapter mPagerAdapter;
    private EditorBottomSheetTabAdapter bottomSheetTabAdapter;
    private FileTreeFragment mFileTreeFragment;
    private EditorFragment mCurrentFragment;
    private TreeNode mLastHeld;
    private SymbolInputView symbolInput;
    private AndroidProject mProject;
    private IDEProject mIDEProject;
    private BuildServiceHandler mBuildServiceHandler;
    private FileOptionsHandler mFileOptionsHandler;
    private QuickAction mTabCloseAction;
    private TextSheetFragment mDaemonStatusFragment;
    private OptionsListFragment mFileOptionsFragment;
    private ProgressSheet mSearchingProgress;
    private AlertDialog mFindInProjectDialog;
    private ActivityResultLauncher<Intent> mUIDesignerLauncher;
    public static File mCurrentFile;
    @SuppressWarnings("rawtypes")
    private EditorBottomSheetBehavior mEditorBottomSheet;
    
    private static final String TAG_FILE_OPTIONS_FRAGMENT = "file_options_fragment";
    private static final Range Range_ofZero = new Range (new Position (0, 0), new Position (0, 0));
    private static final int ACTION_ID_CLOSE = 100;
    private static final int ACTION_ID_OTHERS = 101;
    private static final int ACTION_ID_ALL = 102;
    
    public static final String EXTRA_PROJECT = "project";
    public static final String KEY_BOTTOM_SHEET_SHOWN = "editor_bottomSheetShown";
    
    private final LogReceiver mLogReceiver = new LogReceiver ().setLogListener (this::appendApkLog);
    
    /**
     * MenuItem(s) that are related to the build process
     * <p>
     * These items will be disabled once the build process starts and will be enabled after the execution
     */
    private final int[] BUILD_IDS = {
            R.id.menuEditor_quickRun,
            R.id.menuEditor_runDebug,
            R.id.menuEditor_runRelease,
            R.id.menuEditor_runBuild,
            R.id.menuEditor_runBundle,
            R.id.menuEditor_runClean,
            R.id.menuEditor_runCleanBuild,
            R.id.menuEditor_lint,
            R.id.menuEditor_lintDebug,
            R.id.menuEditor_lintRelease
    };
    
    @Override
    protected View bindLayout () {
        mBinding = ActivityEditorBinding.inflate (getLayoutInflater ());
        mDiagnosticInfoBinding = mBinding.diagnosticInfo;
        return mBinding.getRoot ();
    }
    
    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        setSupportActionBar (mBinding.editorToolbar);
        
        getProjectFromIntent ();
        
        mPagerAdapter = new EditorPagerAdapter (this, this.mProject);
        mFileTreeFragment = FileTreeFragment.newInstance (this.mProject).setFileActionListener (this);
        mDaemonStatusFragment = new TextSheetFragment ().setTextSelectable (true);
        
        setupDrawerToggle ();
        loadFragment (mFileTreeFragment);
        
        symbolInput = new SymbolInputView (this);
        mBinding.bottomSheet.textContainer.addView (symbolInput, 0, new ViewGroup.LayoutParams (-1, -2));
        
        final var mediator = new TabLayoutMediator (mBinding.tabs,
                mBinding.editorViewPager,
                true,
                false, // Do NOT enable smooth scrolls. Doing so results in error. Any workaround or fix will be appreciated.
                (tab, position) -> tab.setText (mPagerAdapter.getEditorTitle (position)));
        mBinding.editorViewPager.setUserInputEnabled (false);
        mBinding.editorViewPager.setOffscreenPageLimit (9);
        mBinding.editorViewPager.setAdapter (mPagerAdapter);
        mBinding.tabs.addOnTabSelectedListener (this);
        mediator.attach ();
        
        setupEditorBottomSheet ();
        
        createQuickActions ();
        
        mBuildServiceHandler = new BuildServiceHandler (this);
        mFileOptionsHandler = new FileOptionsHandler (this);
        
        startServices ();
        
        KeyboardUtils.registerSoftInputChangedListener (this, __ -> onSoftInputChanged ());
        registerLogReceiver ();
        setupContainers ();
        setupSignatureText ();
        setupDiagnosticInfo ();
        
        startLanguageServers ();
        
        mUIDesignerLauncher = registerForActivityResult (new ActivityResultContracts.StartActivityForResult (), this::onGetUIDesignerResult);
    }
    
    private void setupDrawerToggle () {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle (this, mBinding.editorDrawerLayout, mBinding.editorToolbar, R.string.app_name, R.string.app_name);
        mBinding.editorDrawerLayout.addDrawerListener (toggle);
        mBinding.startNav.setNavigationItemSelectedListener (this);
        toggle.syncState ();
    }
    
    private void setupEditorBottomSheet () {
        bottomSheetTabAdapter = new EditorBottomSheetTabAdapter (this);
        mBinding.bottomSheet.pager.setAdapter (bottomSheetTabAdapter);
        
        final var mediator = new TabLayoutMediator (mBinding.bottomSheet.tabs,
                mBinding.bottomSheet.pager,
                true,
                true,
                (tab, position) -> tab.setText (bottomSheetTabAdapter.getTitle (position)));
        
        mediator.attach ();
        mBinding.bottomSheet.pager.setUserInputEnabled (false);
        mBinding.bottomSheet.pager.setOffscreenPageLimit (bottomSheetTabAdapter.getItemCount () - 1);  // DO not remove any views
        
        //noinspection rawtypes
        mEditorBottomSheet = (EditorBottomSheetBehavior) EditorBottomSheetBehavior.from (mBinding.bottomSheet.getRoot ());
        mEditorBottomSheet.setBinding (mBinding.bottomSheet);
        mEditorBottomSheet.addBottomSheetCallback (new BottomSheetBehavior.BottomSheetCallback () {
            @Override
            public void onStateChanged (@NonNull View bottomSheet, int newState) {
                mBinding.bottomSheet.textContainer.setVisibility (newState == BottomSheetBehavior.STATE_EXPANDED ? View.INVISIBLE : View.VISIBLE);
            }
            
            @Override
            public void onSlide (@NonNull View bottomSheet, float slideOffset) {
                mBinding.bottomSheet.textContainer.setAlpha (1f - slideOffset);
            }
        });
        
        if (!getApp ().getPrefManager ().getBoolean (KEY_BOTTOM_SHEET_SHOWN) && mEditorBottomSheet.getState () != BottomSheetBehavior.STATE_EXPANDED) {
            mEditorBottomSheet.setState (BottomSheetBehavior.STATE_EXPANDED);
            
            new Handler (Looper.getMainLooper ()).postDelayed (() -> {
                mEditorBottomSheet.setState (BottomSheetBehavior.STATE_COLLAPSED);
                getApp ().getPrefManager ().putBoolean (KEY_BOTTOM_SHEET_SHOWN, true);
            }, 1500);
        }
    }
    
    @Override
    public void onBackPressed () {
        if (mBinding.getRoot ().isDrawerOpen (GravityCompat.END)) {
            mBinding.getRoot ().closeDrawer (GravityCompat.END);
        } else if (mBinding.getRoot ().isDrawerOpen (GravityCompat.START)) {
            mBinding.getRoot ().closeDrawer (GravityCompat.START);
        } else if (getDaemonStatusFragment ().isShowing ()) {
            getDaemonStatusFragment ().dismiss ();
        } else if (mFileOptionsFragment != null && mFileOptionsFragment.isShowing ()) {
            mFileOptionsFragment.dismiss ();
        } else if (mEditorBottomSheet.getState () == BottomSheetBehavior.STATE_EXPANDED) {
            mEditorBottomSheet.setState (BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            confirmProjectClose ();
        }
    }
    
    @Override
    protected void onPause () {
        new Thread (() -> {
            
            boolean saved;
            try {
                saveAll (false /* No notification */);
                saved = true;
            } catch (Throwable th) {
                LOG.error (getString (R.string.err_cannot_save_files), th);
                saved = false;
            }
            
            if (!saved) {
                ThreadUtils.runOnUiThread (() -> getApp ().toast (R.string.msg_failed_save, Toaster.Type.ERROR));
            }
        }, "AndroidIDE FileSaver").start ();
        super.onPause ();
    }
    
    @Override
    protected void onResume () {
        super.onResume ();
        try {
            mFileTreeFragment.listProjectFiles ();
        } catch (Throwable th) {
            getApp ().toast (R.string.msg_failed_list_files, Toaster.Type.ERROR);
        }
    }
    
    @Override
    protected void onDestroy () {
        closeProject (false);
        try {
            unregisterReceiver (mLogReceiver);
        } catch (Throwable th) {
            LOG.error ("Failed to unregister LogReceiver", th);
        }
        super.onDestroy ();
    }
    
    @Override
    @SuppressLint("AlwaysShowAction")
    public boolean onPrepareOptionsMenu (Menu menu) {
        for (int id : BUILD_IDS) {
            MenuItem item = menu.findItem (id);
            if (item != null) {
                boolean enabled = getBuildService () != null && !getBuildService ().isBuilding ();
                item.setEnabled (enabled);
                item.getIcon ().setAlpha (enabled ? 255 : 76);
            }
        }
        
        MenuItem run1 = menu.findItem (R.id.menuEditor_quickRun);
        MenuItem run2 = menu.findItem (R.id.menuEditor_run);
        MenuItem undo = menu.findItem (R.id.menuEditor_undo);
        MenuItem redo = menu.findItem (R.id.menuEditor_redo);
        MenuItem save = menu.findItem (R.id.menuEditor_save);
        MenuItem def = menu.findItem (R.id.menuEditor_gotoDefinition);
        MenuItem ref = menu.findItem (R.id.menuEditor_findReferences);
        MenuItem comment = menu.findItem (R.id.menuEditor_commentLine);
        MenuItem uncomment = menu.findItem (R.id.menuEditor_uncommentLine);
        MenuItem findFile = menu.findItem (R.id.menuEditor_findFile);
        MenuItem viewLayout = menu.findItem (R.id.menuEditor_viewLayout);
        
        if (KeyboardUtils.isSoftInputVisible (this)) {
            run1.setShowAsAction (MenuItem.SHOW_AS_ACTION_NEVER);
            run2.setShowAsAction (MenuItem.SHOW_AS_ACTION_NEVER);
            undo.setShowAsAction (MenuItem.SHOW_AS_ACTION_ALWAYS);
            redo.setShowAsAction (MenuItem.SHOW_AS_ACTION_ALWAYS);
            
            viewLayout.setShowAsAction (MenuItem.SHOW_AS_ACTION_NEVER);
        } else {
            run1.setShowAsAction (MenuItem.SHOW_AS_ACTION_ALWAYS);
            run2.setShowAsAction (MenuItem.SHOW_AS_ACTION_ALWAYS);
            undo.setShowAsAction (MenuItem.SHOW_AS_ACTION_NEVER);
            redo.setShowAsAction (MenuItem.SHOW_AS_ACTION_NEVER);
            
            viewLayout.setShowAsAction (MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        
        final boolean notNull = mCurrentFile != null;
        final boolean isJava = notNull && mCurrentFile.getName ().endsWith (".java");
        final boolean isXml = notNull && mCurrentFile.getName ().endsWith (".xml");
        final boolean isLayout = isXml && mCurrentFile.getParentFile () != null && Pattern.compile (FileOptionsHandler.LAYOUTRES_PATH_REGEX).matcher (mCurrentFile.getParentFile ().getAbsolutePath ()).matches ();
        final int nullableAlpha = notNull ? 255 : 76;
        final int javaFileAlpha = isJava ? 255 : 76;
        final int layoutFileAlpha = isLayout ? 255 : 76;
        
        undo.setEnabled (notNull);
        redo.setEnabled (notNull);
        save.setEnabled (notNull);
        comment.setEnabled (notNull);
        uncomment.setEnabled (notNull);
        findFile.setEnabled (notNull);
        def.setEnabled (isJava);
        ref.setEnabled (isJava);
        
        undo.getIcon ().setAlpha (nullableAlpha);
        redo.getIcon ().setAlpha (nullableAlpha);
        save.getIcon ().setAlpha (nullableAlpha);
        comment.getIcon ().setAlpha (nullableAlpha);
        uncomment.getIcon ().setAlpha (nullableAlpha);
        findFile.getIcon ().setAlpha (nullableAlpha);
        def.getIcon ().setAlpha (javaFileAlpha);
        ref.getIcon ().setAlpha (javaFileAlpha);
        
        viewLayout.setEnabled (isLayout);
        viewLayout.getIcon ().setAlpha (layoutFileAlpha);
        viewLayout.setShowAsActionFlags (isLayout ? MenuItem.SHOW_AS_ACTION_ALWAYS : MenuItem.SHOW_AS_ACTION_NEVER);
        
        return true;
    }
    
    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu (Menu menu) {
        if (menu instanceof MenuBuilder) {
            MenuBuilder builder = (MenuBuilder) menu;
            builder.setOptionalIconsVisible (true);
        }
        getMenuInflater ().inflate (R.menu.menu_editor, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        if (getBuildService () == null) {
            return false;
        }
        int id = item.getItemId ();
        if (id == R.id.menuEditor_runDebug || id == R.id.menuEditor_quickRun) {
            getBuildServiceHandler ().assembleDebug (true);
        } else if (id == R.id.menuEditor_runRelease) {
            getBuildService ().assembleRelease ();
        } else if (id == R.id.menuEditor_runClean) {
            getBuildService ().clean ();
        } else if (id == R.id.menuEditor_runCleanBuild) {
            getBuildService ().cleanAndRebuild ();
        } else if (id == R.id.menuEditor_runStopDaemons) {
            getBuildService ().stopAllDaemons ();
        } else if (id == R.id.menuEditor_runBuild) {
            getBuildService ().build ();
        } else if (id == R.id.menuEditor_runBundle) {
            getBuildService ().bundle ();
        } else if (id == R.id.menuEditor_lint) {
            getBuildService ().lint ();
        } else if (id == R.id.menuEditor_lintDebug) {
            getBuildService ().lintDebug ();
        } else if (id == R.id.menuEditor_lintRelease) {
            getBuildService ().lintRelease ();
        } else if (id == R.id.menuEditor_save) {
            // * 1. Notify that all files are saved
            // * 2. If there were any XML files modified, call ':app:processDebugResources' task
            // *
            // * This will make sure that we generate view bindings and R.jar at proper time
            // * This will further result in updated code completion
            saveAll (true, true);
        } else if (id == R.id.menuEditor_undo && this.mCurrentFragment != null && this.mCurrentFragment.isVisible ()) {
            this.mCurrentFragment.undo ();
        } else if (id == R.id.menuEditor_redo && this.mCurrentFragment != null && this.mCurrentFragment.isVisible ()) {
            this.mCurrentFragment.redo ();
        } else if (id == R.id.menuEditor_gotoDefinition && mCurrentFragment != null && this.mCurrentFragment.isVisible ()) {
            this.mCurrentFragment.findDefinition ();
        } else if (id == R.id.menuEditor_findReferences && mCurrentFragment != null && this.mCurrentFragment.isVisible ()) {
            this.mCurrentFragment.findReferences ();
        } else if (id == R.id.menuEditor_commentLine && mCurrentFragment != null && this.mCurrentFragment.isVisible ()) {
            this.mCurrentFragment.commentLine ();
        } else if (id == R.id.menuEditor_uncommentLine && mCurrentFragment != null && this.mCurrentFragment.isVisible ()) {
            this.mCurrentFragment.uncommentLine ();
        } else if (id == R.id.menuEditor_findFile && mCurrentFragment != null && this.mCurrentFragment.isVisible ()) {
            this.mCurrentFragment.beginSearch ();
        } else if (id == R.id.menuEditor_findProject) {
            AlertDialog d = getFindInProjectDialog ();
            if (d != null) {
                d.show ();
            }
        } else if (id == R.id.menuEditor_viewLayout && mCurrentFile != null) {
            previewLayout ();
        }
        invalidateOptionsMenu ();
        return true;
    }
    
    @Override
    public EditorActivity provide () {
        return this;
    }
    
    @Override
    public EditorActivity provideEditorActivity () {
        return this;
    }
    
    @Override
    public AndroidProject provideAndroidProject () {
        return mProject;
    }
    
    @Override
    public IDEProject provideIDEProject () {
        return mIDEProject;
    }
    
    public void handleSearchResults (Map<File, List<SearchResult>> results) {
        setSearchResultAdapter (new com.itsaky.androidide.adapters.SearchListAdapter (results, file -> {
            openFile (file);
            hideViewOptions ();
        }, match -> {
            openFileAndSelect (match.file, match);
            hideViewOptions ();
        }));
        
        showSearchResults ();
        
        if (mSearchingProgress != null && mSearchingProgress.isShowing ()) {
            mSearchingProgress.dismiss ();
        }
    }
    
    public void appendApkLog (LogLine line) {
        bottomSheetTabAdapter.getLogFragment ().appendLog (line);
    }
    
    public void setIDEProject (IDEProject project) {
        this.mIDEProject = project;
    }
    
    public void appendBuildOut (final String str) {
        bottomSheetTabAdapter.getBuildOutputFragment ().appendOutput (str);
    }
    
    public void showDaemonStatus () {
        ShellServer shell = getApp ().newShell (t -> getDaemonStatusFragment ().append (t));
        shell.bgAppend (String.format ("echo '%s'", getString (R.string.msg_getting_daemom_status)));
        shell.bgAppend (String.format ("cd '%s' && sh gradlew --status", mProject.getProjectPath ()));
        if (!getDaemonStatusFragment ().isShowing ()) {
            getDaemonStatusFragment ().show (getSupportFragmentManager (), "daemon_status");
        }
    }
    
    public void hideViewOptions () {
        if (mEditorBottomSheet.getState () != BottomSheetBehavior.STATE_COLLAPSED) {
            mEditorBottomSheet.setState (BottomSheetBehavior.STATE_COLLAPSED);
        }
    }
    
    public void showSearchResults () {
        if (mEditorBottomSheet.getState () != BottomSheetBehavior.STATE_EXPANDED) {
            mEditorBottomSheet.setState (BottomSheetBehavior.STATE_EXPANDED);
        }
        
        final int index = bottomSheetTabAdapter.findIndexOfFragmentByClass (SearchResultFragment.class);
        if (index >= 0 && index < mBinding.bottomSheet.tabs.getTabCount ()) {
            final var tab = mBinding.bottomSheet.tabs.getTabAt (index);
            if (tab != null) {
                tab.select ();
            }
        }
    }
    
    public void handleDiagnosticsResultVisibility (boolean errorVisible) {
        bottomSheetTabAdapter.getDiagnosticsFragment ().handleResultVisibility (errorVisible);
    }
    
    public void handleSearchResultVisibility (boolean errorVisible) {
        bottomSheetTabAdapter.getSearchResultFragment ().handleResultVisibility (errorVisible);
    }
    
    public void setStatus (final CharSequence text) {
        try {
            runOnUiThread (() -> mBinding.bottomSheet.statusText.setText (text));
        } catch (Throwable th) {
            LOG.error ("Failed to update status text", th);
        }
    }
    
    public void showFirstBuildNotice () {
        DialogUtils.newMaterialDialogBuilder (this)
                .setPositiveButton (android.R.string.ok, null)
                .setTitle (R.string.title_first_build)
                .setMessage (R.string.msg_first_build)
                .setCancelable (false)
                .create ().show ();
    }
    
    @Override
    public void onGroupClick (DiagnosticGroup group) {
        if (group != null
                && group.file != null
                && group.file.exists ()
                && FileUtils.isUtf8 (group.file)) {
            openFile (group.file);
            hideViewOptions ();
        }
    }
    
    @Override
    public void onDiagnosticClick (File file, @NonNull Diagnostic diagnostic) {
        openFileAndSelect (file, diagnostic.getRange ());
        hideViewOptions ();
    }
    
    public EditorPagerAdapter getPagerAdapter () {
        return mPagerAdapter;
    }
    
    public ActivityEditorBinding getBinding () {
        return mBinding;
    }
    
    public LayoutDiagnosticInfoBinding getDiagnosticBinding () {
        return mDiagnosticInfoBinding;
    }
    
    /**
     * Positions the view to the provided coordinates and within screen
     *
     * @param view     View to position
     * @param initialX Initial X coordinate of view
     * @param initialY Initial Y coordinate of view
     */
    public void positionViewWithinScreen (@NonNull final View view, final float initialX, final float initialY) {
        view.setX (initialX);
        view.setY (initialY);
        
        final Rect r = new Rect ();
        final int width = view.getWidth ();
        view.getWindowVisibleDisplayFrame (r);
        if (r.width () != width) {
            
            // Will be true when the view is going out of screen to left
            if (initialX < r.left) {
                view.setX (SizeUtils.dp2px (8)); // an offset of 8dp from the left edge of screen
            }
            
            // Will be true when the view is going out of screen to right
            if (initialX + width > r.right) {
                view.setX (r.right - SizeUtils.dp2px (8) - width);  // position to the right but leaving 8dp space from the right edge of screen
            }
        }
    }
    
    public void openFileAndSelect (File file, org.eclipse.lsp4j.Range range) {
        openFile (file, range);
        EditorFragment opened = mPagerAdapter.findEditorByFile (file);
        if (opened != null && opened.getEditor () != null) {
            CodeEditor editor = opened.getEditor ();
            editor.post (() -> {
                if (LSPUtils.isEqual (range.getStart (), range.getEnd ())) {
                    editor.setSelection (range.getStart ().getLine (), range.getEnd ().getCharacter ());
                } else {
                    editor.setSelectionRegion (range.getStart ().getLine (), range.getStart ().getCharacter (), range.getEnd ().getLine (), range.getEnd ().getCharacter ());
                }
            });
        }
    }
    
    @Override
    public void
    onTabSelected (@NonNull TabLayout.Tab tab) {
        final var opened = mPagerAdapter.getOpenedFile (tab.getPosition ());
        if (opened != null && opened.file != null && opened.fragment != null) {
            mCurrentFragment = opened.fragment;
            mCurrentFile = opened.file;
            refreshSymbolInput (mCurrentFragment);
        }
        
        invalidateOptionsMenu ();
    }
    
    @Override
    public void onTabUnselected (@NonNull TabLayout.Tab tab) {
        final var opened = mPagerAdapter.getOpenedFile (tab.getPosition ());
        if (opened == null) {
            return;
        }
        
        final var frag = opened.fragment;
        boolean isGradle = frag.isModified () && opened.file.getName ().endsWith (EditorFragment.EXT_GRADLE);
        frag.save ();
        if (isGradle) {
            notifySyncNeeded ();
        }
    }
    
    @Override
    public void onTabReselected (TabLayout.Tab p1) {
        mTabCloseAction.show (mBinding.tabs);
    }
    
    @Override
    public boolean onNavigationItemSelected (@NonNull MenuItem p1) {
        final int id = p1.getItemId ();
        if (id == R.id.editornav_discuss) {
            getApp ().openTelegramGroup ();
        } else if (id == R.id.editornav_suggest) {
            getApp ().openGitHub ();
        } else if (id == R.id.editornav_needHelp) {
            showNeedHelpDialog ();
        } else if (id == R.id.editornav_settings) {
            startActivity (new Intent (this, PreferencesActivity.class));
        } else if (id == R.id.editornav_share) {
            startActivity (IntentUtils.getShareTextIntent (getString (R.string.msg_share_app)));
        } else if (id == R.id.editornav_close_project) {
            confirmProjectClose ();
        } else if (id == R.id.editornav_terminal) {
            openTerminal ();
        }
        mBinding.getRoot ().closeDrawer (GravityCompat.START);
        return false;
    }
    
    public void loadFragment (Fragment fragment) {
        super.loadFragment (fragment, mBinding.editorFrameLayout.getId ());
    }
    
    public FileTreeFragment getFileTreeFragment () {
        return mFileTreeFragment;
    }
    
    public TreeNode getLastHoldTreeNode () {
        return mLastHeld;
    }
    
    public OptionsListFragment getFileOptionsFragment (File file) {
        mFileOptionsFragment = new OptionsListFragment ();
        mFileOptionsFragment.addOption (new SheetOption (0, R.drawable.ic_file_copy_path, R.string.copy_path, file));
        mFileOptionsFragment.addOption (new SheetOption (1, R.drawable.ic_file_rename, R.string.rename_file, file));
        mFileOptionsFragment.addOption (new SheetOption (2, R.drawable.ic_delete, R.string.delete_file, file));
        if (file.isDirectory ()) {
            mFileOptionsFragment.addOption (new SheetOption (3, R.drawable.ic_new_file, R.string.new_file, file));
            mFileOptionsFragment.addOption (new SheetOption (4, R.drawable.ic_new_folder, R.string.new_folder, file));
        }
        mFileOptionsFragment.setOnOptionsClickListener (mFileOptionsHandler);
        return mFileOptionsFragment;
    }
    
    public ProgressSheet getProgressSheet (int msg) {
        if (mSearchingProgress != null && mSearchingProgress.isShowing ()) {
            mSearchingProgress.dismiss ();
        }
        mSearchingProgress = new ProgressSheet ();
        mSearchingProgress.setCancelable (false);
        mSearchingProgress.setWelcomeTextEnabled (false);
        mSearchingProgress.setMessage (getString (msg));
        mSearchingProgress.setSubMessageEnabled (false);
        return mSearchingProgress;
    }
    
    public MaterialBanner getSyncBanner () {
        return mBinding.syncBanner.setContentTextColor (ContextCompat.getColor (this, R.color.primaryTextColor))
                .setBannerBackgroundColor (ContextCompat.getColor (this, R.color.primaryLightColor))
                .setButtonTextColor (ContextCompat.getColor (this, R.color.secondaryColor))
                .setIcon (R.drawable.ic_sync)
                .setContentText (R.string.msg_sync_needed);
    }
    
    public TextSheetFragment getDaemonStatusFragment () {
        return mDaemonStatusFragment == null ? mDaemonStatusFragment = new TextSheetFragment ().setTextSelectable (true).setTitleText (R.string.gradle_daemon_status) : mDaemonStatusFragment;
    }
    
    public void setDiagnosticsAdapter (@NonNull final DiagnosticsAdapter adapter) {
        bottomSheetTabAdapter.getDiagnosticsFragment ().setAdapter (adapter);
    }
    
    public void setSearchResultAdapter (@NonNull final SearchListAdapter adapter) {
        bottomSheetTabAdapter.getSearchResultFragment ().setAdapter (adapter);
    }
    
    @Override
    public EditorFragment openFile (File file) {
        return openFile (file, null);
    }
    
    public EditorFragment openFile (File file, org.eclipse.lsp4j.Range selection) {
        if (selection == null) {
            selection = Range_ofZero;
        }
        int i = mPagerAdapter.openFile (file, selection, this);
        final var tab = mBinding.tabs.getTabAt (i);
        if (tab != null && i >= 0 && !tab.isSelected ()) {
            tab.select ();
        }
        
        if (mBinding.editorDrawerLayout.isDrawerOpen (GravityCompat.END)) {
            mBinding.editorDrawerLayout.closeDrawer (GravityCompat.END);
        }
        
        invalidateOptionsMenu ();
        try {
            return mPagerAdapter.getFrag (i);
        } catch (Throwable th) {
            return null;
        }
    }
    
    @Override
    public void onOpenSuccessful (File file, String text) {
        // textDocument/didOpen is now handled by CodeEditor
    }
    
    @Override
    public void showFileOptions (File thisFile, TreeNode node) {
        mLastHeld = node;
        getFileOptionsFragment (thisFile).show (getSupportFragmentManager (), TAG_FILE_OPTIONS_FRAGMENT);
    }
    
    public boolean saveAll () {
        return saveAll (true);
    }
    
    public boolean saveAll (boolean notify) {
        return saveAll (notify, false);
    }
    
    public boolean saveAll (boolean notify, boolean canProcessResources) {
        SaveResult result = mPagerAdapter.saveAll ();
        if (notify) {
            getApp ().toast (R.string.all_saved, Toaster.Type.SUCCESS);
        }
        if (result.gradleSaved) {
            notifySyncNeeded ();
        }
        if (result.xmlSaved && canProcessResources && getBuildService () != null) {
            getBuildService ().updateResourceClasses ();
        }
        return result.gradleSaved;
    }
    
    public void install (@NonNull File apk) {
        if (apk.exists ()) {
            Intent i = IntentUtils.getInstallAppIntent (apk);
            if (i != null) {
                startActivity (i);
            }
        }
    }
    
    public void createServices () {
        new TaskExecutor ().executeAsync (() -> {
            IDELanguageServer javaServer = LSPProvider.getServerForLanguage (LSPProvider.LANGUAGE_JAVA);
            if (javaServer == null) {
                return null;
            }
            
            List<String> cps = mProject.getClassPaths ();
            JsonObject settings = new JsonObject ();
            JsonObject java = new JsonObject ();
            JsonArray classPath = new JsonArray ();
            
            for (int i = 0; i < cps.size (); i++) {
                classPath.add (cps.get (i));
            }
            
            java.add ("classPath", classPath);
            settings.add ("java", java);
            
            DidChangeConfigurationParams params = new DidChangeConfigurationParams ();
            params.setSettings (settings);
            
            javaServer.getWorkspaceService ().didChangeConfiguration (params);
            
            return null;
        }, __ -> setStatus (getString (getApp ().isXmlServiceStarted () ? R.string.msg_service_started : R.string.msg_starting_completion_failed)));
    }
    
    public void closeFile (int index) {
        closeFile (index, true);
    }
    
    public void closeFile (int index, boolean selectOther) {
//        mBinding.tabs.removeOnTabSelectedListener (this);
        mPagerAdapter.closeFileAt (index);
//        mBinding.tabs.addOnTabSelectedListener (this);
        
        if (mPagerAdapter.getItemCount () <= 0) {
            mCurrentFragment = null;
            mCurrentFile = null;
        }
        
        invalidateOptionsMenu ();
        
        if (mPagerAdapter.getItemCount () == 0 &&
                (mBinding.editorViewPager.getChildCount () != 0 || mBinding.tabs.getChildCount () != 0)) {
            // TODO Find out why this happens
            //    Mostly for java files...
            mBinding.editorViewPager.removeAllViews ();
            mBinding.tabs.removeAllViews ();
        }
    }
    
    public void closeAll () {
        mBinding.tabs.removeOnTabSelectedListener (this);
        mPagerAdapter.closeAllFiles ();
        mBinding.tabs.addOnTabSelectedListener (this);
    }
    
    public void closeOthers () {
        mPagerAdapter.closeOthers (mBinding.tabs.getSelectedTabPosition ());
    }
    
    /////////////////////////////////////////////////
    ////////////// PRIVATE APIS /////////////////////
    /////////////////////////////////////////////////
    
    @Contract(pure = true)
    private void onGetUIDesignerResult (@NonNull ActivityResult result) {
        if (mCurrentFragment != null && mCurrentFragment.getEditor () != null && result.getResultCode () == RESULT_OK) {
            final var data = result.getData ();
            if (data != null && data.hasExtra (DesignerActivity.KEY_GENERATED_CODE)) {
                final var code = data.getStringExtra (DesignerActivity.KEY_GENERATED_CODE);
                mCurrentFragment.getEditor ().setText (code);
                saveAll ();
            } else {
                final var msg = getString (R.string.msg_invalid_designer_result);
                getApp ().toast (msg, Toaster.Type.ERROR);
                LOG.error (msg, "Data returned by UI Designer is null or is invalid.");
            }
        } else {
            LOG.error ("UI Designer returned an invalid result code.", "Result code: " + result.getResultCode ());
        }
    }
    
    
    private void previewLayout () {
        try {
            saveAll (false);
            if (getApp ().getLayoutInflater () == null) {
                getApp ().createInflater (getApp ().createInflaterConfig (getContextProvider (), getResourceDirectories ()));
            }
            
            final Intent intent = new Intent (this, DesignerActivity.class);
            intent.putExtra (DesignerActivity.KEY_LAYOUT_PATH, mCurrentFile.getAbsolutePath ());
            mUIDesignerLauncher.launch (intent);
            
        } catch (Throwable th) {
            LOG.error (getString (R.string.err_cannot_preview_layout), th);
            getApp ().toast (R.string.msg_cannot_preview_layout, Toaster.Type.ERROR);
        }
    }
    
    @NonNull
    @Contract(" -> new")
    private ILayoutInflater.ContextProvider getContextProvider () {
        return this::provide;
    }
    
    @NonNull
    private Set<File> getResourceDirectories () {
        final Set<File> dirs = new HashSet<> ();
        if (mProject != null && mProject.getModulePaths () != null && !mProject.getModulePaths ().isEmpty ()) {
            for (String path : mProject.getModulePaths ()) {
                if (path != null && new File (path).exists ()) {
                    File res = new File (path, "src/main/res");
                    if (res.exists ()) {
                        dirs.add (res);
                    }
                }
            }
        }
        return dirs;
    }
    
    private void openTerminal () {
        final Intent intent = new Intent (this, TerminalActivity.class);
        intent.putExtra (TerminalActivity.KEY_WORKING_DIRECTORY, mProject.getProjectPath ());
        startActivity (intent);
    }
    
    private void startLanguageServers () {
        LSP.setActivityProvider (this);
        LSP.Java.start (() -> {
            Optional<InitializeResult> result = LSP.Java.init (mProject.getProjectPath ());
            if (result.isPresent ()) {
                LSP.Java.initialized ();
            }
        });
    }
    
    private void getProjectFromIntent () {
        this.mProject = getIntent ().getParcelableExtra (EXTRA_PROJECT);
        getApp ().getPrefManager ().setOpenedProject (this.mProject.getProjectPath ());
    }
    
    private void setupSignatureText () {
        GradientDrawable gd = new GradientDrawable ();
        gd.setShape (GradientDrawable.RECTANGLE);
        gd.setColor (0xff212121);
        gd.setStroke (1, 0xffffffff);
        gd.setCornerRadius (8);
        mBinding.symbolText.setBackground (gd);
        mBinding.symbolText.setVisibility (View.GONE);
    }
    
    private void setupDiagnosticInfo () {
        GradientDrawable gd = new GradientDrawable ();
        gd.setShape (GradientDrawable.RECTANGLE);
        gd.setColor (0xff212121);
        gd.setStroke (1, 0xffffffff);
        gd.setCornerRadius (8);
        mDiagnosticInfoBinding.getRoot ().setBackground (gd);
        mDiagnosticInfoBinding.getRoot ().setVisibility (View.GONE);
    }
    
    private void setupContainers () {
        handleDiagnosticsResultVisibility (true);
        handleSearchResultVisibility (true);
    }
    
    private void startServices () {
        
        if (!IDELanguageClientImpl.isInitialized ()) {
            IDELanguageClientImpl.initialize (this);
        }
        
        // Actually, we don't need to start FileOptionsHandler
        // Because it would work anyway
        // But still we do...
        mFileOptionsHandler.start ();
        
        mBuildServiceHandler.start ();
        getBuildServiceHandler ().assembleDebug (false);
    }
    
    private void onSoftInputChanged () {
        invalidateOptionsMenu ();
        if (KeyboardUtils.isSoftInputVisible (this)) {
            TransitionManager.beginDelayedTransition (mBinding.getRoot (), new Slide (Gravity.TOP));
            symbolInput.setVisibility (View.VISIBLE);
            mBinding.bottomSheet.statusText.setVisibility (View.GONE);
            mBinding.bottomSheet.swipeHint.setVisibility (View.GONE);
        } else {
            TransitionManager.beginDelayedTransition (mBinding.getRoot (), new Slide (Gravity.BOTTOM));
            symbolInput.setVisibility (View.GONE);
            mBinding.bottomSheet.statusText.setVisibility (View.VISIBLE);
            mBinding.bottomSheet.swipeHint.setVisibility (View.VISIBLE);
        }
    }
    
    private void refreshSymbolInput (@NonNull EditorFragment frag) {
        if (frag.getEditor () == null || frag.getFile () == null) {
            return;
        }
        
        symbolInput.bindEditor (frag.getEditor ());
        symbolInput.setSymbols (Symbols.forFile (frag.getFile ()));
    }
    
    private void notifySyncNeeded () {
        if (getBuildService () != null && !getBuildService ().isBuilding ()) {
            getSyncBanner ()
                    .setNegative (android.R.string.cancel, null)
                    .setPositive (android.R.string.ok, v -> getBuildServiceHandler ().assembleDebug (false))
                    .show ();
        }
    }
    
    private void closeProject (boolean manualFinish) {
        if (getBuildService () != null) {
            getBuildService ().setListener (null);
            getBuildService ().exit ();
        }
        
        // Make sure we close files
        // This fill further make sure that file contents are not erased.
        closeAll ();
        
        getApp ().getPrefManager ().setOpenedProject (PreferenceManager.NO_OPENED_PROJECT);
        
        LSP.shutdownAll ();
        
        getApp ().stopAllDaemons ();
        
        if (manualFinish) {
            finish ();
        }
    }
    
    private void confirmProjectClose () {
        final MaterialAlertDialogBuilder builder = DialogUtils.newMaterialDialogBuilder (this);
        builder.setTitle (R.string.title_confirm_project_close);
        builder.setMessage (R.string.msg_confirm_project_close);
        builder.setNegativeButton (android.R.string.no, null);
        builder.setPositiveButton (android.R.string.yes, (d, w) -> {
            d.dismiss ();
            closeProject (true);
        });
        builder.show ();
    }
    
    private AlertDialog getFindInProjectDialog () {
        return mFindInProjectDialog == null ? createFindInProjectDialog () : mFindInProjectDialog;
    }
    
    @Nullable
    private AlertDialog createFindInProjectDialog () {
        if (mProject == null
                || mProject.getModulePaths () == null
                || mProject.getModulePaths ().size () <= 0) {
            getApp ().toast (R.string.msg_no_modules, Toaster.Type.ERROR);
            return null;
        }
        final List<String> modules = mProject.getModulePaths ();
        final List<File> srcDirs = new ArrayList<> ();
        final LayoutSearchProjectBinding binding = LayoutSearchProjectBinding.inflate (getLayoutInflater ());
        binding.modulesContainer.removeAllViews ();
        for (int i = 0; i < modules.size (); i++) {
            final File file = new File (modules.get (i));
            final File src = new File (file, "src");
            if (!file.exists () || !file.isDirectory () || !src.exists () || !src.isDirectory ()) {
                continue;
            }
            
            CheckBox check = new CheckBox (this);
            check.setText (file.getName ());
            check.setChecked (true);
            
            LinearLayout.MarginLayoutParams params = new LinearLayout.MarginLayoutParams (-2, -2);
            params.bottomMargin = SizeUtils.dp2px (4);
            binding.modulesContainer.addView (check, params);
            
            srcDirs.add (src);
        }
        final MaterialAlertDialogBuilder builder = DialogUtils.newMaterialDialogBuilder (this);
        builder.setTitle (R.string.menu_find_project);
        builder.setView (binding.getRoot ());
        builder.setCancelable (false);
        builder.setPositiveButton (R.string.menu_find, (dialog, which) -> {
            final String text = Objects.requireNonNull (binding.input.getEditText ()).getText ().toString ().trim ();
            if (text.isEmpty ()) {
                getApp ().toast (R.string.msg_empty_search_query, Toaster.Type.ERROR);
                return;
            }
            
            final List<File> searchDirs = new ArrayList<> ();
            for (int i = 0; i < binding.modulesContainer.getChildCount (); i++) {
                CheckBox check = (CheckBox) binding.modulesContainer.getChildAt (i);
                if (check.isChecked ()) {
                    searchDirs.add (srcDirs.get (i));
                }
            }
            
            final String extensions = Objects.requireNonNull (binding.filter.getEditText ()).getText ().toString ().trim ();
            final List<String> extensionList = new ArrayList<> ();
            if (!extensions.isEmpty ()) {
                if (extensions.contains ("|")) {
                    for (String str : extensions.split (Pattern.quote ("|"))) {
                        if (str == null || str.trim ().isEmpty ()) {
                            continue;
                        }
                        
                        extensionList.add (str);
                    }
                } else {
                    extensionList.add (extensions);
                }
            }
            
            if (searchDirs.isEmpty ()) {
                getApp ().toast (R.string.msg_select_search_modules, Toaster.Type.ERROR);
            } else {
                dialog.dismiss ();
                getProgressSheet (R.string.msg_searching_project).show (getSupportFragmentManager (), "search_in_project_progress");
                RecursiveFileSearcher.searchRecursiveAsync (text, extensionList, searchDirs, this::handleSearchResults);
            }
        });
        builder.setNegativeButton (android.R.string.cancel, (__, ___) -> __.dismiss ());
        mFindInProjectDialog = builder.create ();
        return mFindInProjectDialog;
    }
    
    private void registerLogReceiver () {
        IntentFilter filter = new IntentFilter ();
        filter.addAction (LogReceiver.APPEND_LOG);
        registerReceiver (mLogReceiver, filter);
    }
    
    private IDEService getBuildService () {
        return mBuildServiceHandler.getService ();
    }
    
    public BuildServiceHandler getBuildServiceHandler () {
        return mBuildServiceHandler;
    }
    
    private void showNeedHelpDialog () {
        MaterialAlertDialogBuilder builder = DialogUtils.newMaterialDialogBuilder (this);
        builder.setTitle (R.string.need_help);
        builder.setMessage (R.string.msg_need_help);
        builder.setPositiveButton (android.R.string.ok, null);
        builder.create ().show ();
    }
    
    private void createQuickActions () {
        ActionItem closeThis = new ActionItem (ACTION_ID_CLOSE, getString (R.string.action_closeThis), R.drawable.ic_close_this);
        ActionItem closeOthers = new ActionItem (ACTION_ID_OTHERS, getString (R.string.action_closeOthers), R.drawable.ic_close_others);
        ActionItem closeAll = new ActionItem (ACTION_ID_ALL, getString (R.string.action_closeAll), R.drawable.ic_close_all);
        mTabCloseAction = new QuickAction (this, QuickAction.HORIZONTAL);
        mTabCloseAction.addActionItem (closeThis, closeOthers, closeAll);
        mTabCloseAction.setColorRes (R.color.tabAction_background);
        mTabCloseAction.setTextColorRes (R.color.tabAction_text);
        
        mTabCloseAction.setOnActionItemClickListener ((item) -> {
            final int id = item.getActionId ();
            saveAll ();
            if (id == ACTION_ID_CLOSE) {
                closeFile (mBinding.tabs.getSelectedTabPosition ());
            }
            
            if (id == ACTION_ID_OTHERS) {
                closeOthers ();
                closeOthers ();
            }
            
            if (id == ACTION_ID_ALL) {
                closeAll ();
            }
        });
    }
    
    private static final Logger LOG = Logger.instance ("EditorActivity");
}
