package com.mistofjudgement.playlistdl;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.mistofjudgement.playlistdl.databinding.FragmentFirstBinding;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private boolean downloading = false;

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher, as an instance variable.
    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // feature requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }
            });

    private Handler handler;
    private Executor executor;
    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        handler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDownload();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public static boolean isValidYoutubeUrl(String url) {
        // YouTube video ID regex pattern
//        Pattern pattern = Pattern.compile("^.*(?:youtu.be\\/|v\\/|u/\\w/|embed\\/|watch\\|?v=)([\\w-]{11}).*$");
        // Check if the URL matches the pattern
//        Matcher matcher = pattern.matcher(url);
//        return matcher.matches();
        //i dont even care
        return !url.isEmpty();
    }


    @SuppressLint("DefaultLocale")
    protected void startDownload() {
        if(downloading) {
            Toast.makeText(FirstFragment.this.getContext(), "hold up wait a moment. im already downloading", Toast.LENGTH_LONG).show();
            return;
        }
        // dont care?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if(!isStoragePermissionGranted()) {
                Toast.makeText(this.getContext(), "i need perms.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        String url = binding.playlisturl.getText().toString().trim();
        if(!isValidYoutubeUrl(url)) {
            Toast.makeText(this.getContext(), R.string.invalid_url, Toast.LENGTH_LONG).show();
            return;
        }

        YoutubeDLRequest req = new YoutubeDLRequest(url);
        File dldir = getDownloadLocation();
//        File config = new File(dldir, "config.txt");
        req.addOption("--no-mtime");
        req.addOption("-x");
        req.addOption("--embed-thumbnail");
        req.addOption("--embed-metadata");
        req.addOption("--audio-format", "mp3");
//        req.addOption("-f", "bestaudio[ext=mp4]");
        req.addOption("-o", dldir.getAbsolutePath() + "/%(title)s.%(ext)s");

        downloading = true;
        executor.execute(() -> {
            try {

                YoutubeDL.getInstance().execute(req, (progress, etaInSeconds, line) -> {
                    this.requireActivity().runOnUiThread(() -> {
                        Log.i(MainActivity.TAG, line);
                        binding.status.setText(String.format("%.2f%% %d seconds left\n%s", progress, etaInSeconds, line));

                    });
                });
            } catch (YoutubeDLException | InterruptedException e) {
                e.printStackTrace();
            }
            String[] paths = new String[dldir.list().length];
            downloading = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                paths = (String[]) Arrays.stream(Objects.requireNonNull(dldir.list())).map(s -> dldir.getAbsolutePath()+"/" + s).toArray(value -> new String[dldir.list().length]);
            }
            // Tell the media scanner about the new file so that it is
            // immediately available to the user.
            MediaScannerConnection.scanFile(this.requireContext(),
                    paths, new String[]{MediaStore.Audio.Media.CONTENT_TYPE},
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i("ExternalStorage", "Scanned " + path + ":");
                            Log.i("ExternalStorage", "-> uri=" + uri);
                        }
                    });
//            requireActivity().sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))));

        });

    }
    @RequiresApi(api = Build.VERSION_CODES.R)
    public boolean isStoragePermissionGranted() {
        if (ContextCompat.checkSelfPermission(
                this.requireContext(), Manifest.permission.MANAGE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED) {
            // You can use the API that requires the permission.
        } else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            requestPermissionLauncher.launch(
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE);
        }
        return true;
    }

    @NonNull
    private File getDownloadLocation() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File youtubeDLDir = new File(downloadsDir, "youtubedl-android");
        if (!youtubeDLDir.exists()) youtubeDLDir.mkdir();
        return youtubeDLDir;
    }


}