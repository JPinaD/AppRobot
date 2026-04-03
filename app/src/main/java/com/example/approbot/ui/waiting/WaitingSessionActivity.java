package com.example.approbot.ui.waiting;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.approbot.R;
import com.example.approbot.network.NsdAdvertiser;
import com.example.approbot.network.TcpServer;
import com.example.approbot.util.AppConstants;

public class WaitingSessionActivity extends AppCompatActivity {

    private NsdAdvertiser nsdAdvertiser;
    private TcpServer tcpServer;
    private TextView tvNetworkStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_session);

        nsdAdvertiser = new NsdAdvertiser(this);
        tcpServer = new TcpServer(AppConstants.NSD_DEFAULT_PORT, (message, out) -> {
            if (AppConstants.MSG_PING.equals(message)) {
                out.println(AppConstants.MSG_PONG);
            }
            runOnUiThread(() -> tvNetworkStatus.setText(
                    getString(R.string.network_status_connected)));
        });

        tvNetworkStatus = findViewById(R.id.tvNetworkStatus);

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        TextView tvSelectedProfileName = findViewById(R.id.tvSelectedProfileName);
        TextView tvSelectedProfileDescription = findViewById(R.id.tvSelectedProfileDescription);
        tvSelectedProfileName.setText(getIntent().getStringExtra("profile_name"));
        tvSelectedProfileDescription.setText(getIntent().getStringExtra("profile_description"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        tvNetworkStatus.setText(getString(R.string.network_status_waiting));
        tcpServer.start();
        nsdAdvertiser.start("Robot-1", AppConstants.NSD_DEFAULT_PORT);
    }

    @Override
    protected void onStop() {
        super.onStop();
        nsdAdvertiser.stop();
        tcpServer.stop();
    }
}
