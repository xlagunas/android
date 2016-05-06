/**
 * Copyright (c) 2016 Mattermost, Inc. All Rights Reserved.
 * See License.txt for license information.
 */
package net.opentrends.mattermost;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import net.opentrends.model.User;
import net.opentrends.service.IResultListener;
import net.opentrends.service.MattermostService;
import net.opentrends.service.Promise;

public class SignupActivity extends AppChildActivity {

    EditText emailAddress;
    TextView errorMessage;
    Button proceed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_signup);

        errorMessage = (TextView) findViewById(R.id.error_message);
        emailAddress = (EditText) findViewById(R.id.email_address);
        proceed = (Button) findViewById(R.id.proceed);

        proceed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSignup();
            }
        });
    }

    private void doSignup() {
        String email = emailAddress.getText().toString();
        service.signup(email, service.getTeam()).then(new IResultListener<User>() {
            @Override
            public void onResult(Promise<User> promise) {
                if (promise.getError() != null) {
                    errorMessage.setText(promise.getError());
                } else {
                    errorMessage.setText("");
                    AppActivity.alert("Signup successful", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                }
            }
        });
    }
}
