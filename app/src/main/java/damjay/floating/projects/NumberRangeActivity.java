package damjay.floating.projects;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;

public class NumberRangeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_number_range);

        final EditText minNumber = findViewById(R.id.min_number);
        final EditText maxNumber = findViewById(R.id.max_number);
        Button generateButton = findViewById(R.id.generate_button);
        final TextView resultDisplay = findViewById(R.id.result_display);
        Button minimizeButton = findViewById(R.id.minimize_button);

        generateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int min = Integer.parseInt(minNumber.getText().toString());
                int max = Integer.parseInt(maxNumber.getText().toString());
                if (min <= max) {
                    Random random = new Random();
                    int result = random.nextInt(max - min + 1) + min;
                    resultDisplay.setText(String.valueOf(result));
                } else {
                    resultDisplay.setText("Invalid range");
                }
            }
        });

        minimizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(NumberRangeActivity.this, NumberRangeService.class);
                startService(intent);
                finish();
            }
        });
    }
} 