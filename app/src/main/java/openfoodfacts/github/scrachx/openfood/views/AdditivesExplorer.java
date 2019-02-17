package openfoodfacts.github.scrachx.openfood.views;

import android.Manifest;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.afollestad.materialdialogs.MaterialDialog;

import org.greenrobot.greendao.async.AsyncSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.Single;
import openfoodfacts.github.scrachx.openfood.FastScroller;
import openfoodfacts.github.scrachx.openfood.R;
import openfoodfacts.github.scrachx.openfood.models.Additive;
import openfoodfacts.github.scrachx.openfood.models.AdditiveName;
import openfoodfacts.github.scrachx.openfood.models.AdditiveNameDao;
import openfoodfacts.github.scrachx.openfood.models.DaoSession;
import openfoodfacts.github.scrachx.openfood.repositories.IProductRepository;
import openfoodfacts.github.scrachx.openfood.repositories.ProductRepository;
import openfoodfacts.github.scrachx.openfood.utils.SearchType;
import openfoodfacts.github.scrachx.openfood.utils.Utils;
import openfoodfacts.github.scrachx.openfood.views.adapters.AdditivesAdapter;

public class AdditivesExplorer extends BaseActivity implements AdditivesAdapter.ClickListener {


    private RecyclerView recyclerView;
    private List<AdditiveName> additives;
    private Toolbar toolbar;
    @BindView(R.id.buttonScan)
    FloatingActionButton mButtonScan;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_additives_explorer);

        recyclerView = findViewById(R.id.additiveRecyclerView);
        toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.additives);

        DaoSession daoSession = Utils.getAppDaoSession(this);
        AsyncSession asyncSessionAdditives = daoSession.startAsyncSession();
        AdditiveNameDao additiveNameDao = daoSession.getAdditiveNameDao();

        String languageCode = Locale.getDefault().getLanguage();
        asyncSessionAdditives.queryList(additiveNameDao.queryBuilder()
                .where(AdditiveNameDao.Properties.LanguageCode.eq(languageCode))
                .where(AdditiveNameDao.Properties.Name.like("E%")).build());

        additives = new ArrayList<>();
        asyncSessionAdditives.setListenerMainThread(operation -> {
            additives = (List<AdditiveName>) operation.getResult();

            Collections.sort(additives, (additiveName, t1) -> {
                String s1 = additiveName.getName().toLowerCase().replace('x', '0').split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")[1];
                String s2 = t1.getName().toLowerCase().replace('x', '0').split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")[1];
                return Integer.valueOf(s1).compareTo(Integer.valueOf(s2));
            });


            recyclerView.setLayoutManager(new LinearLayoutManager(AdditivesExplorer.this));
            recyclerView.setAdapter(new AdditivesAdapter(additives, AdditivesExplorer.this));
            recyclerView.addItemDecoration(new DividerItemDecoration(AdditivesExplorer.this, DividerItemDecoration.VERTICAL));

        });


    }


    @Override
    public void onClick(int position, String name) {
        ProductBrowsingListActivity.startActivity(AdditivesExplorer.this, name, SearchType.ADDITIVE);
    }

    @OnClick(R.id.buttonScan)
    protected void onButtonScanClick() {
        if (Utils.isHardwareCameraInstalled(this)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                    new MaterialDialog.Builder(this)
                            .title(R.string.action_about)
                            .content(R.string.permission_camera)
                            .neutralText(R.string.txtOk)
                            .onNeutral((dialog, which) -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, Utils.MY_PERMISSIONS_REQUEST_CAMERA))
                            .show();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, Utils.MY_PERMISSIONS_REQUEST_CAMERA);
                }
            } else {
                Intent intent = new Intent(this, ContinuousScanActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        SearchManager searchManager = (SearchManager) this.getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchItem = menu.findItem(R.id.action_search);

        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.addtive_search));
        if (searchManager.getSearchableInfo(this.getComponentName()) != null) {

            searchView.setSearchableInfo(searchManager.getSearchableInfo(this.getComponentName()));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {

                    List<AdditiveName> additiveNames = new ArrayList<>();

                    for (int i = 0; i < additives.size(); i++) {
                        if (additives.get(i).getName().toLowerCase().split(" - ").length > 1) {
                            String[] additiveContent = additives.get(i).getName().toLowerCase().split(" - ");
                            if (additiveContent[0].trim().contains(newText.trim().toLowerCase()) || additiveContent[1].trim().contains(newText.trim().toLowerCase())
                                    || (additiveContent[0]+"-"+additiveContent[1]).contains(newText.trim().toLowerCase())) {
                                additiveNames.add(additives.get(i));
                            }
                        }
                    }

                    recyclerView.setAdapter(new AdditivesAdapter(additiveNames, AdditivesExplorer.this));
                    recyclerView.getAdapter().notifyDataSetChanged();


                    return false;
                }
            });

        }


        return super.onCreateOptionsMenu(menu);
    }
}


