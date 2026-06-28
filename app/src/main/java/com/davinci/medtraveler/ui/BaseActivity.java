package com.davinci.medtraveler.ui;

import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.AuthManager;
import com.davinci.medtraveler.data.CatalogMeta;
import com.google.android.material.navigation.NavigationView;

public abstract class BaseActivity extends AppCompatActivity {

    private DrawerLayout drawer;
    private ProgressBar topBar;
    private NavigationView nav;

    protected void setContentWithChrome(@LayoutRes int contentLayoutRes) {
        drawer = new DrawerLayout(this);
        drawer.setLayoutParams(new DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Toolbar toolbar = new Toolbar(this);
        toolbar.setTitle(R.string.app_name);
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar_background));
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_on_primary));
        col.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LayoutInflater.from(this).inflate(contentLayoutRes, content, true);

        topBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        topBar.setIndeterminate(true);
        topBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.progress_height));
        tp.gravity = Gravity.TOP;
        content.addView(topBar, tp);
        col.addView(content);

        drawer.addView(col);

        nav = new NavigationView(this);
        DrawerLayout.LayoutParams np = new DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        np.gravity = Gravity.START;
        nav.setLayoutParams(np);
        nav.inflateMenu(R.menu.drawer_menu);
        View header = LayoutInflater.from(this).inflate(R.layout.nav_header, nav, false);
        nav.addHeaderView(header);
        bindDbStatus(header);
        nav.setNavigationItemSelectedListener(item -> {
            drawer.closeDrawers();
            int id = item.getItemId();
            if (id == R.id.nav_search) startActivity(new Intent(this, SearchActivity.class));
            else if (id == R.id.nav_contact) openContact();
            else if (id == R.id.nav_account) openAccount();
            return true;
        });
        drawer.addView(nav);

        super.setContentView(drawer);

        setSupportActionBar(toolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.drawer_open, R.string.drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        toggle.getDrawerArrowDrawable().setColor(ContextCompat.getColor(this, R.color.text_on_primary));
    }

    protected void refreshDbStatus() {
        if (nav != null && nav.getHeaderCount() > 0) bindDbStatus(nav.getHeaderView(0));
    }

    private void bindDbStatus(View header) {
        TextView t = header.findViewById(R.id.txt_db_status);
        String last = new CatalogMeta(this).lastUpdatedGlobal();
        t.setText(last.isEmpty() ? getString(R.string.db_status_never)
                : getString(R.string.db_status_format, last));
    }

    public void showProgress(boolean show) {
        runOnUiThread(() -> { if (topBar != null) topBar.setVisibility(show ? View.VISIBLE : View.GONE); });
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_update, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_update_db) {
            onUpdateDbRequested();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Keep the drawer header's DB date current after background downloads
        // update it from other screens or background executors.
        refreshDbStatus();
    }

    protected void onUpdateDbRequested() { }

    private void openAccount() {
        boolean logged = new AuthManager().isLoggedIn();
        startActivity(new Intent(this, logged ? ProfileActivity.class : AuthActivity.class));
    }

    private void openContact() {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + getString(R.string.contact_email)));
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.contact_subject));
        if (i.resolveActivity(getPackageManager()) == null) {
            android.widget.Toast.makeText(this, R.string.contact_no_app,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(Intent.createChooser(i, getString(R.string.menu_contact)));
    }
}
