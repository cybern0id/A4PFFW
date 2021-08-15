/*
 * Copyright (C) 2017-2021 Soner Tari
 *
 * This file is part of PFFW.
 *
 * PFFW is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PFFW is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PFFW.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.comixwall.pffw;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.drawerlayout.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.firebase.messaging.FirebaseMessaging;

import java.nio.charset.Charset;

import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.MainActivity.sendToken;
import static org.comixwall.pffw.MainActivity.token;
import static org.comixwall.pffw.Utils.processException;
import static org.comixwall.pffw.Utils.showMessage;

public class Login extends Fragment implements ControllerTask.ControllerTaskListener {

    private ProgressBar pbProgress;

    private AutoCompleteTextView tvUser;
    private TextInputEditText etPassword;
    private TextInputEditText etHost;
    private TextInputEditText etPort;

    private String mUser;
    private String mPassword;
    private String mHost;
    private int mPort;

    private String mLastError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.login, container, false);

        pbProgress = view.findViewById(R.id.progress);

        tvUser = view.findViewById(R.id.user);
        etPassword = view.findViewById(R.id.password);
        etHost = view.findViewById(R.id.host);
        etPort = view.findViewById(R.id.port);

        Button btnButton = view.findViewById(R.id.button);
        btnButton.setOnClickListener(mButtonClickHandler);

        // Disable drawer and toggle button, appbar menu items are not created if the fragment is Login
        ((MainActivity) getActivity()).drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        ((MainActivity) getActivity()).toggle.setDrawerIndicatorEnabled(false);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;
    }

    @Override
    public void executePreTask() {
    }

    @Override
    public void preExecute() {
        pbProgress.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean executeTask() {
        try {
            logger.finest("setAuthParams: " + mUser + ", " + mPassword + ", " + mHost + ", " + mPort);
            controller.login(mUser, mPassword, mHost, mPort);
        } catch (Exception e) {
            mLastError = processException(e);
            return false;
        }
        return true;
    }

    @Override
    public void postExecute(boolean result) {
        if (result) {
            processLogin();
        } else {
            showMessage(this, "Error: " + mLastError);
        }

        pbProgress.setVisibility(View.GONE);
    }

    @Override
    public void executeOnCancelled() {
        pbProgress.setVisibility(View.GONE);
    }

    private void processLogin() {
        if (controller.isLoggedin()) {
            // Reset the cache upon login
            FragmentManager fm = getActivity().getSupportFragmentManager();
            fm.beginTransaction().remove(fm.findFragmentByTag("cache")).commit();

            MainActivity.cache = new Cache();
            fm.beginTransaction().add(MainActivity.cache, "cache").commit();

            // Enable drawer and toggle button
            ((MainActivity) getActivity()).drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            ((MainActivity) getActivity()).toggle.setDrawerIndicatorEnabled(true);

            // Recreate menu items, the fragment is Dashboard now
            getActivity().invalidateOptionsMenu();

            try {
                // Hide the soft keyboard
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            } catch (Exception ignored) {}

           // Send the token to the system upon each login, so the app can get notifications
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
                @Override
                public void onComplete(@NonNull Task<String> task) {
                    token = task.getResult();
                    // Controller sends the token before the next command it executes, and lowers this sendToken flag
                    sendToken = true;
                }
            });

            ((MainActivity) getActivity()).showFirstFragment();
        }
    }

    private void login() {
        ControllerTask.run(this, this);
    }

    private final View.OnClickListener mButtonClickHandler = new View.OnClickListener() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onClick(View view) {
            int id = view.getId();

            if (id == R.id.button) {
                mUser = tvUser.getText().toString();

                // ATTENTION: Encrypt the password immediately.
                HashCode hashCode = Hashing.sha1().hashString(etPassword.getText().toString(), Charset.defaultCharset());
                mPassword = hashCode.toString();

                mHost = etHost.getText().toString();

                boolean applyDefaultPort = false;
                try {
                    mPort = Integer.parseInt(etPort.getText().toString());
                    if (mPort < 1 || mPort > 65535) {
                        applyDefaultPort = true;
                    }
                } catch (Exception e) {
                    applyDefaultPort = true;
                }

                if (applyDefaultPort) {
                    mPort = 22;
                    etPort.setText(Integer.toString(mPort));
                }

                login();
            }
        }
    };
}

