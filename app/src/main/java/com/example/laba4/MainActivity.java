package com.example.laba4;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 100;
    private static final int REQ_IMAGE = 101;
    private static final int REQ_CAMERA_PERMISSION = 102;

    private static final String TESS_FOLDER = "tesseract";
    private static final String TESS_LANG = "rus";

    private ImageView imagePreview;
    private TextView resultText;
    private ProgressBar progressBar;

    private Bitmap currentBitmap;
    private TessBaseAPI tessBaseAPI;
    private String tessDataPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imagePreview = findViewById(R.id.imagePreview);
        resultText = findViewById(R.id.resultText);
        progressBar = findViewById(R.id.progressBar);

        Button btnCamera = findViewById(R.id.btnCamera);
        Button btnGallery = findViewById(R.id.btnGallery);
        Button btnRecognize = findViewById(R.id.btnRecognize);

        btnCamera.setOnClickListener(v -> openCamera());
        btnGallery.setOnClickListener(v -> openGallery());
        btnRecognize.setOnClickListener(v -> recognizeText());

        initTesseract();
    }

    private void initTesseract() {
        try {
            File tessDir = new File(getFilesDir(), TESS_FOLDER);
            File tessDataDir = new File(tessDir, "tessdata");

            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs();
            }

            copyAssetIfNeeded(
                    "tessdata/rus.traineddata",
                    new File(tessDataDir, "rus.traineddata")
            );

            tessDataPath = tessDir.getAbsolutePath();

            tessBaseAPI = new TessBaseAPI();

            boolean success = tessBaseAPI.init(tessDataPath, TESS_LANG);

            if (!success) {
                resultText.setText("Ошибка: Tesseract не смог загрузить rus.traineddata");
                tessBaseAPI.recycle();
                tessBaseAPI = null;
                return;
            }

            tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);

            resultText.setText("Tesseract OCR готов. Русский язык загружен.");

        } catch (Exception e) {
            resultText.setText("Ошибка инициализации Tesseract: " + e.getMessage());
        }
    }

    private void copyAssetIfNeeded(String assetPath, File outFile) throws Exception {
        if (outFile.exists() && outFile.length() > 0) {
            return;
        }

        InputStream inputStream = getAssets().open(assetPath);
        OutputStream outputStream = new FileOutputStream(outFile);

        byte[] buffer = new byte[8192];
        int length;

        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }

        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    private void openCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    REQ_CAMERA_PERMISSION
            );
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, REQ_CAMERA);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQ_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_CAMERA_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Нет разрешения на камеру", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        try {
            if (requestCode == REQ_CAMERA) {
                Bundle extras = data.getExtras();

                if (extras != null) {
                    currentBitmap = (Bitmap) extras.get("data");
                }

            } else if (requestCode == REQ_IMAGE) {
                Uri uri = data.getData();

                if (uri != null) {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    currentBitmap = BitmapFactory.decodeStream(inputStream);

                    if (inputStream != null) {
                        inputStream.close();
                    }
                }
            }

            if (currentBitmap != null) {
                imagePreview.setImageBitmap(currentBitmap);
                resultText.setText("Изображение выбрано. Нажми «Распознать».");
            }

        } catch (Exception e) {
            resultText.setText("Ошибка изображения: " + e.getMessage());
        }
    }

    private void recognizeText() {
        if (currentBitmap == null) {
            Toast.makeText(this, "Сначала выбери изображение", Toast.LENGTH_SHORT).show();
            return;
        }

        if (tessBaseAPI == null) {
            Toast.makeText(this, "Tesseract не загружен", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        resultText.setText("Распознавание русского текста...");

        new Thread(() -> {
            try {
                Bitmap preparedBitmap = prepareBitmapForOcr(currentBitmap);

                tessBaseAPI.setImage(preparedBitmap);

                String text = tessBaseAPI.getUTF8Text();

                tessBaseAPI.clear();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (text == null || text.trim().isEmpty()) {
                        resultText.setText("Текст не найден");
                    } else {
                        resultText.setText(text.trim());
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    resultText.setText("Ошибка OCR: " + e.getMessage());
                });
            }
        }).start();
    }

    private Bitmap prepareBitmapForOcr(Bitmap source) {
        Bitmap bitmap = source.copy(Bitmap.Config.ARGB_8888, true);

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int[] pixels = new int[width * height];

        bitmap.getPixels(
                pixels,
                0,
                width,
                0,
                0,
                width,
                height
        );

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];

            int r = Color.red(pixel);
            int g = Color.green(pixel);
            int b = Color.blue(pixel);

            int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);

            int value = gray > 170 ? 255 : 0;

            pixels[i] = Color.rgb(value, value, value);
        }

        bitmap.setPixels(
                pixels,
                0,
                width,
                0,
                0,
                width,
                height
        );

        return bitmap;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (tessBaseAPI != null) {
            tessBaseAPI.recycle();
            tessBaseAPI = null;
        }
    }
}