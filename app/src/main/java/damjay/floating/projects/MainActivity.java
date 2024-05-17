package damjay.floating.projects;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Html;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import damjay.floating.projects.calculate.CalculatorService;

public class MainActivity extends AppCompatActivity {
    public static final int FLOAT_PERMISSION_REQUEST = 100;

    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().setTitle(Html.fromHtml("<font color='#ffffff'>" + getResources().getString(R.string.floating_pdf) + "</font>"));

        findViewById(R.id.floating_pdf).setOnClickListener(getClickListener(FloatingPDFActivity.class));
        findViewById(R.id.floating_calculator).setOnClickListener(getServiceClickListener(CalculatorService.class));
        findViewById(R.id.floating_bible).setOnClickListener(getBibleClickListener());

        // Request for optional optimization
        checkBatteryOptimization();
        // Request for compulsory permissions
//      checkPermissions();
    }

    private View.OnClickListener getBibleClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, R.string.bible_todo, Toast.LENGTH_LONG).show();
            }
        };
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        // Is battery optimization enabled?
        if (!((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(MainActivity.class.getPackage().getName())) {
            // If a dialog is open, don't open another
            if (alertDialog != null) return;
            alertDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.ignore_battery_optimization)
                .setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        closeAlertDialog();
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        closeAlertDialog();
                        checkPermissions();
                    }
                })
                .setCancelable(false)
                .create();
            alertDialog.show();
        } else {
            checkPermissions();
        }
    }

    private boolean checkPermissions() {
        // Check if display over apps permission is emabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            if (alertDialog != null) return false;
            alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.grant_permissions)
                .setMessage(R.string.display_permission_message)
                .setCancelable(false)
                .setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                   Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, FLOAT_PERMISSION_REQUEST);
                    }
                })
                .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                }).create();
            alertDialog.show();
            return false;
        } else {
            closeAlertDialog();
            return true;
        }
        // TODO: Request for management of all files
    }

    private View.OnClickListener getClickListener(final Class<?> clazz) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, clazz);
                startActivity(intent);
            }
        };
    }

    private View.OnClickListener getServiceClickListener(final Class clazz) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, clazz);
                startService(intent);
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FLOAT_PERMISSION_REQUEST && resultCode == RESULT_OK) {
            closeAlertDialog();
        } else {
            if (checkPermissions()) {
                closeAlertDialog();
            }
        }
    }

    private void closeAlertDialog() {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (alertDialog == null)
            checkPermissions();
    }

}
