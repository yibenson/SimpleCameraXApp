package com.example.simplecameraapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.simplecameraapp.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    // permissions variables
    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 200;

    // camera variables
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private CameraSelector cameraSelector;
    private ExecutorService analysisExecutor;
    private ExecutorService captureExecutor;
    private FaceDetector faceDetector;
    private int rotation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!hasPermissions()) {
            // request camera permissions
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE_CAMERA_PERMISSION);
        } else {
            // CameraX preview and image capture use cases
            bindUseCases();
        }
    }

    private void bindUseCases() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();
                cameraProvider.unbindAll();
                bindPreview(cameraProvider);
                bindImageAnalysis(cameraProvider);
                bindImageCapture(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // Should never be reached
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        PreviewView previewView = findViewById(R.id.previewView);
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview);
    }

    private void bindImageAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().build();
        analysisExecutor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(analysisExecutor, image -> {
            rotation = image.getImageInfo().getRotationDegrees();
        });
        cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageAnalysis);
    }

    private void bindImageCapture(ProcessCameraProvider cameraProvider) {
        ImageCapture imageCapture =
                new ImageCapture.Builder()
                        .setTargetRotation(rotation).build();
        Button captureButton = findViewById(R.id.captureButton);
        captureExecutor = Executors.newSingleThreadExecutor();
        captureButton.setOnClickListener(v ->
                imageCapture.takePicture(captureExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                saveFace(image);
                showToast("Image captured successfully");
            }
        }));
        cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageCapture);
    }

    private void saveFace(ImageProxy image) {
        @SuppressLint("UnsafeOptInUsageError") Bitmap bitmap = FileUtils.toBitmap(image);
        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
        faceDetector = FaceDetection.getClient();
        faceDetector.process(inputImage).addOnSuccessListener(faces -> {
            if (!faces.isEmpty()) {
                Face face = faces.get(0);
                Rect rect = face.getBoundingBox();
                try {
                    Bitmap croppedBmp = Bitmap.createBitmap(bitmap, rect.left,
                            bitmap.getHeight() - rect.bottom, rect.width(), rect.height());
                    Bitmap compressedBmp = Bitmap.createScaledBitmap(croppedBmp, 150, 150, false);
                    FileUtils.saveImage(compressedBmp, this);
                } catch (IllegalArgumentException e) {
                    // face not in bounds
                }
            }
            image.close();
        });
    }

    public void showToast(final String toast)
    {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this,
                        "Please enable all permissions to use this app",
                        Toast.LENGTH_LONG)
                        .show();
                finish();
            }
            bindUseCases();
        }
    }

    private boolean hasPermissions() {
        for (String s: PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, s) !=
                    PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }



}

