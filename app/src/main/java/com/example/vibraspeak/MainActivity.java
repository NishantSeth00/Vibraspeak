package com.example.vibraspeak;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextView shapeTextView;
    private TextToSpeech textToSpeech;
    private Vibrator vibrator;
    private ExecutorService cameraExecutor;
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA, Manifest.permission.VIBRATE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.previewView);
        shapeTextView = findViewById(R.id.shapeTextView);

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV initialization failed");
        } else {
            Log.d(TAG, "OpenCV initialized successfully");
        }

        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(Locale.US);
            }
        });

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private boolean allPermissionsGranted() {
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    if (image == null || image.getImage() == null) return;

                    @OptIn(markerClass = ExperimentalGetImage.class) Image mediaImage = image.getImage();
                    Mat mat = imageToMat(mediaImage);

                    if (mat != null) {
                        detectShapes(mat);
                    }

                    image.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        (LifecycleOwner) this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private Mat imageToMat(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        Mat yuv = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CvType.CV_8UC1);
        yuv.put(0, 0, nv21);

        Mat rgb = new Mat();
        Imgproc.cvtColor(yuv, rgb, Imgproc.COLOR_YUV2RGB_NV21, 3);

        return rgb;
    }

    private void detectShapes(Mat mat) {
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);
        Imgproc.Canny(gray, gray, 75, 200);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(gray, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < 1500) continue;

            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double peri = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approx, 0.04 * peri, true);

            int vertices = (int) approx.total();
            String shape = "";

            if (vertices == 3) {
                shape = "Triangle";
            } else if (vertices == 4) {
                Rect rect = Imgproc.boundingRect(contour);
                float aspectRatio = (float) rect.width / rect.height;
                shape = (aspectRatio >= 0.95 && aspectRatio <= 1.05) ? "Square" : "Rectangle";
            } else if (vertices == 5) {
                shape = "Pentagon";
            } else if (vertices == 6) {
                shape = "Hexagon";
            } else {
                shape = "Circle";
            }

            String finalShape = shape;
            runOnUiThread(() -> {
                shapeTextView.setText(finalShape);
                speakShape(finalShape);
                vibrateMorseCode(finalShape);
            });

            break; // show first found shape only
        }
    }

    private void speakShape(String shape) {
        if (textToSpeech != null) {
            textToSpeech.speak(shape, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void vibrateMorseCode(String shapeName) {
        if (vibrator == null || shapeName == null) return;

        Map<Character, String> morseMap = new HashMap() {{
            put('A', ".-");   put('B', "-...");
            put('C', "-.-."); put('D', "-..");
            put('E', ".");    put('F', "..-.");
            put('G', "--.");  put('H', "....");
            put('I', "..");   put('J', ".---");
            put('K', "-.-");  put('L', ".-..");
            put('M', "--");   put('N', "-.");
            put('O', "---");  put('P', ".--.");
            put('Q', "--.-"); put('R', ".-.");
            put('S', "...");  put('T', "-");
            put('U', "..-");  put('V', "...-");
            put('W', ".--");  put('X', "-..-");
            put('Y', "-.--"); put('Z', "--..");
        }};

        List<Long> pattern = new ArrayList<>();
        pattern.add(0L); // start immediately

        for (char ch : shapeName.toUpperCase().toCharArray()) {
            String morse = morseMap.get(ch);
            if (morse == null) continue;

            for (char symbol : morse.toCharArray()) {
                if (symbol == '.') {
                    pattern.add(100L); // dot
                } else if (symbol == '-') {
                    pattern.add(300L); // dash
                }
                pattern.add(100L); // pause between dot/dash
            }
            pattern.add(300L); // pause between letters
        }

        long[] vibrationPattern = new long[0];
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            vibrationPattern = pattern.stream().mapToLong(i -> i).toArray();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            startCamera();
        } else {
            finish();
        }
    }
}