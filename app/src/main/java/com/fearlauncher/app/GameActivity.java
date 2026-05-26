package com.fearlauncher.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class GameActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // This activity will be used when the game launches
        // You can add game surface view or other game-related UI here
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up game resources when activity is destroyed
    }
}
