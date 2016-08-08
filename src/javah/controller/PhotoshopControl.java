package javah.controller;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamUtils;
import com.github.sarxos.webcam.util.ImageUtils;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javah.util.DraggableRectangle;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * This class will handle the uploading and capturing of photos.
 */
public class PhotoshopControl {

    static interface OnPhotoshopListener {
        void onAcceptButtonClicked(byte client, WritableImage image);
        void onCancelButtonClicked();
    }

    @FXML private Label mActionLabel;
    @FXML private Pane mRootPane;
    @FXML private ImageView mPhotoView;
    @FXML private Pane mImagePane;
    @FXML private HBox mFilterSignatureBox;
    @FXML private ImageView mAcceptButton;
    @FXML private ImageView mCaptureButton;
    @FXML private CheckBox mFilterSignatureCheckbox;

    // The possible clients requesting a photo from the PhotoshopControl.
    public static final byte
            CLIENT_RESIDENT_PHOTO = 0,
            CLIENT_CHAIRMAN_PHOTO = 1,
            CLIENT_CHAIRMAN_SIGNATURE = 2,
            CLIENT_SECRETARY_SIGNATURE = 3,
            CLIENT_ID_SIGNATURE = 4;

    private byte mClient, mRequest;

    // The possible requests of the clients.
    public static byte
            REQUEST_PHOTO_CAPTURE = 0,
            REQUEST_PHOTO_UPLOAD = 1;

    /**
     * Used for : REQUEST_PHOTO_UPLOAD.
     * Holds the uploaded image.
     */
    private Image mUploadedImage;

    /**
     * Used for : REQUEST_PHOTO_CAPTURE
     * Captured image is flipped, so we need to mirror it and make a reference out of it.
     */
    private WritableImage mCapturedImage;

    /**
     * This is the image to be passed to the clients. This image are the cropped images and can either be filtered
     * depending on the client's request.
     */
    private WritableImage mModifiedImage;

    private DraggableRectangle mDraggableRectangle;

    private OnPhotoshopListener mListener;

    private WebcamPanel mWebcamPanel;

    /**
     * In JavaFX, Panels cannot be added to a pane. It must be first converted to a node. Thus, this serves as a container
     * for mWebcamPanel;
     */
    private SwingNode mWebcamNode;

    /**
     * Used for : REQUEST_PHOTO_CAPTURE
     * Determines whether the capture button is pressed and when the client wants to recapture another photo.
     * Value is false if an image is not yet captured and true if an image is captured.
     */
    private boolean mIsImageCaptured = false;

    @FXML
    private void initialize() {
        System.out.println("Photoshop Control Initialized");

        mDraggableRectangle = new DraggableRectangle(
                (int) mImagePane.getPrefWidth(),
                (int) mImagePane.getPrefHeight());

        mImagePane.getChildren().add(mDraggableRectangle);
    }

    /**
     * mModifiedImage is first cropped before being sent to the client.
     * @param mouseEvent
     */
    @FXML
    public void onAcceptButtonClicked(MouseEvent mouseEvent) {

        switch (mClient) {
            case CLIENT_RESIDENT_PHOTO:
            case CLIENT_CHAIRMAN_PHOTO:
                // Crop the mUploadedImage based on the mDraggableRectangle and store it in mModified image before being
                // sent to the client.
                int rectWidth = (int) mDraggableRectangle.getWidth();
                int rectHeight = (int) mDraggableRectangle.getHeight();
                int rectX = (int) mDraggableRectangle.getX();
                int rectY = (int) mDraggableRectangle.getY();
                int uploadedImageWidth = (int) mUploadedImage.getWidth();
                int uploadedImageHeight = (int) mUploadedImage.getHeight();

                PixelReader pixelReader = mUploadedImage.getPixelReader();

                mModifiedImage = new WritableImage(rectWidth, rectHeight);
                PixelWriter pixelWriter = mModifiedImage.getPixelWriter();

                // If ever the draggable rectangle goes out of bounds from the width and height of the uploaded image,
                // then assign transparent pixels on to the out of bounds area.
                for (int x = rectX; x < rectWidth + rectX; x++)
                    for (int y = rectY; y < rectHeight + rectY; y++)

                        pixelWriter.setArgb(x - rectX, y - rectY,
                                (x < uploadedImageWidth && y < uploadedImageHeight) ?
                                        pixelReader.getArgb(x, y) : new Color(0, 0, 0, 0).getRGB());

                break;

            default:
            WritableImage tempImage = mModifiedImage;

            mModifiedImage = new WritableImage(
                    tempImage.getPixelReader(),
                    (int) mDraggableRectangle.getX(),
                    (int) mDraggableRectangle.getY(),
                    (int) mDraggableRectangle.getWidth(),
                    (int) mDraggableRectangle.getHeight());
        }

        mListener.onAcceptButtonClicked(mClient, mModifiedImage);

        // Close the webcam panel.
        if (mWebcamPanel != null) {
            mWebcamPanel.stop();
            mWebcamPanel = null;
            mImagePane.getChildren().remove(mWebcamNode);
        }
    }

    /**
     * Used for : REQUEST_PHOTO_CAPTURE
     * A Button that acts as a way to take a retake a picture.
     * @param mouseEvent
     */
    @FXML
    public void onCaptureButtonClicked(MouseEvent mouseEvent) {
        // If an image is already captured, then recapture an image.
        if (mIsImageCaptured) {

            // While no image is captured by the web cam, only display the web cam capture button.
            mAcceptButton.setVisible(false);
            mAcceptButton.setManaged(false);
            mCaptureButton.setVisible(true);
            mCaptureButton.setManaged(true);
            mFilterSignatureBox.setVisible(false);

            // Send the mPhotoView and mDraggableRectangle to the back to show the web cam pane again.
            mPhotoView.toBack();
            mDraggableRectangle.toBackPref();

            mIsImageCaptured = false;

        // If no image is captured, then capture an image and update the scene.
        } else {
            // Capture an image. . .

            // Create a temp file to hold the captured photo.
            String tempFilePath = System.getenv("PUBLIC") + "/Barangay131/Photos/2d6aeb19-b1ab-4e40-b8af-cfe62a05c431.png";
            File tempFile = new File(tempFilePath);

            // Store photo as a temporary file. Sadly, captured photo is mirrored.
            WebcamUtils.capture(mWebcamPanel.getWebcam(), tempFile, ImageUtils.FORMAT_PNG);

            // Flip the captured image and store it to mCapturedImage.
            try {
                BufferedImage tempImage = ImageIO.read(tempFile);

                // Initialize mModifiedImage to load the flipped image.
                mCapturedImage = new WritableImage(tempImage.getWidth(), tempImage.getHeight());

                PixelWriter pixelWriter = mCapturedImage.getPixelWriter();

                int height = tempImage.getHeight();
                int width = tempImage.getWidth();

                // The flipping process...
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < width; x++)
                        pixelWriter.setArgb(width - 1 - x, y, tempImage.getRGB(x, y));

                switch (mClient) {
                    case CLIENT_SECRETARY_SIGNATURE:
                    case CLIENT_CHAIRMAN_SIGNATURE:
                    case CLIENT_ID_SIGNATURE:
                        mFilterSignatureBox.setVisible(true);

                        // Create a filtered copy of the signature, then store it to mModifiedImage.
                        PixelReader pixelReader = mCapturedImage.getPixelReader();

                        mModifiedImage = new WritableImage(width, height);
                        pixelWriter = mModifiedImage.getPixelWriter();

                        for (int x = 0; x < width; x++)
                            for (int y = 0; y < height; y++) {
                                // Get the rgb of the pixel of the mSignatureImage at (x,y).
                                Color rgb = new Color(pixelReader.getArgb(x, y));

                                int r = rgb.getRed();
                                int g = rgb.getGreen();
                                int b = rgb.getBlue();

                                double z = 0.2126 * r + 0.7152 * g + 0.0722 * b;

                                // Check to see if the pixel is light or dark colored.
                                // If the pixel is light colored, then don't write it in the mFilteredSignatureImage.
                                pixelWriter.setArgb(x, y, z < 128 ? rgb.getRGB() : new Color(0, 0, 0, 0).getRGB());
                            }

                        break;
                    default:
                }

                mAcceptButton.setVisible(true);
                mAcceptButton.setManaged(true);
                mPhotoView.setImage(mCapturedImage);

                mPhotoView.toFront();
                mDraggableRectangle.toFrontPref();

            } catch (IOException e) {
                e.printStackTrace();
            }

            // Update the GUI after a web cam capture is made.
            mAcceptButton.setVisible(true);
            mAcceptButton.setManaged(true);
            mCaptureButton.setVisible(true);
            mCaptureButton.setManaged(true);
            mFilterSignatureBox.setVisible(true);

            mPhotoView.toFront();
            mDraggableRectangle.toFrontPref();

            mIsImageCaptured = true;
        }
    }

    @FXML
    public void onCancelButtonClicked(ActionEvent actionEvent) {
        mListener.onCancelButtonClicked();

        // Close the webcam panel.
        if (mWebcamPanel != null) {
            mWebcamPanel.stop();
            mWebcamPanel = null;
            mImagePane.getChildren().remove(mWebcamNode);
        }
    }

    /**
     * Filter the photo if the photo is a signature type.
     * @param actionEvent
     */
    @FXML
    public void onFilterSignatureCheckboxClicked(ActionEvent actionEvent) {
        mPhotoView.setImage(mFilterSignatureCheckbox.isSelected() ? mModifiedImage :
                mRequest == REQUEST_PHOTO_UPLOAD ? mUploadedImage : mCapturedImage);
    }

    /**
     * Update the scene depending on the client and their request.
     * @param client
     * @param request
     */
    public void setClient(byte client, byte request) {
        mClient = client;
        mRequest = request;

        if (request == REQUEST_PHOTO_UPLOAD) {

            // Open the dialog for photo uploading. . .

            // Make sure that the root pane is unclickable until the dialog is closed.
            mRootPane.setDisable(true);

            // Setup the file chooser dialog.
            FileChooser fileChooser = new FileChooser();

            // Define the allowed file extensions.
            FileChooser.ExtensionFilter extFilterJPG = new FileChooser.ExtensionFilter("JPG Files (*.jpg)", "*.JPG");
            FileChooser.ExtensionFilter extFilterPNG = new FileChooser.ExtensionFilter("PNG Files (*.png)", "*.PNG");
            FileChooser.ExtensionFilter extFilterJPEG = new FileChooser.ExtensionFilter("JPEG Files (*.jpeg)", "*.JPEG");
            fileChooser.getExtensionFilters().addAll(extFilterJPG, extFilterJPEG, extFilterPNG);

            // Show the file chooser dialog.
            Stage stage = new Stage();
            stage.setTitle("Choose Photo");
            File file = fileChooser.showOpenDialog(stage);

            // Enable the mRootPane after the upload window is displayed.
            mRootPane.setDisable(false);

            // Get the path of the uploaded image and display the image at the photo view.
            // If no image is chosen, then cancel the request.
            if (file != null)
                // The image should be resized to 640x480 while preserving ratio.
                mUploadedImage = new Image("file:" + file.toPath(), 640, 480, true, true);
            else {
                mListener.onCancelButtonClicked();
                return;
            }

            // Loading of the proper visual of the upload scene starts. . .

            // Update the functionality of the scene depending whether the client is requesting a profile photo or a
            // signature photo.
            switch (client) {
                case CLIENT_CHAIRMAN_PHOTO :
                case CLIENT_RESIDENT_PHOTO :
                    // mDraggableRectangle is set to a square.
                    mDraggableRectangle.setWidth(216);
                    mDraggableRectangle.setHeight(216);

                    // Make sure that the mSignatureFilterBox is hidden.
                    mFilterSignatureBox.setVisible(false);
                    break;

                default:
                    // mDraggableRectangle is set to a rectangle.
                    mDraggableRectangle.setWidth(384);
                    mDraggableRectangle.setHeight(216);

                    // Make sure that the mSignatureFilterBox is shown.
                    mFilterSignatureBox.setVisible(true);
                    // todo : set selected calls the callback function?
                    mFilterSignatureCheckbox.setSelected(false);

                    // Create a filtered copy of the signature, then store it to mModifiedImage.
                    try {
                        int width = (int) mUploadedImage.getWidth();
                        int height = (int) mUploadedImage.getHeight();
                        PixelReader pixelReader = mUploadedImage.getPixelReader();

                        mModifiedImage = new WritableImage(width, height);
                        PixelWriter pixelWriter = mModifiedImage.getPixelWriter();

                        for (int x = 0; x < width; x++) {
                            for (int y = 0; y < height; y++) {
                                // Get the rgb of the pixel of the mSignatureImage at (x,y).
                                Color rgb = new Color(pixelReader.getArgb(x, y));

                                int r = rgb.getRed();
                                int g = rgb.getGreen();
                                int b = rgb.getBlue();

                                double z = 0.2126 * r + 0.7152 * g + 0.0722 * b;

                                // Check to see if the pixel is light or dark colored.
                                // If the pixel is light colored, then don't write it in the mFilteredSignatureImage.
                                pixelWriter.setArgb(x, y, z < 128 ? rgb.getRGB() : new Color(0, 0, 0, 0).getRGB());
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
            }

            // Update the scene based on the intersecting state of the display and signature photos.
            mActionLabel.setText("Photo Upload");
            mCaptureButton.setVisible(false);
            mCaptureButton.setManaged(false);
            mAcceptButton.setVisible(true);
            mAcceptButton.setManaged(true);
            mPhotoView.setImage(mUploadedImage);

            // Center the mDraggableRectangle.
            mDraggableRectangle.setX(mImagePane.getWidth() / 2 - mDraggableRectangle.getWidth() / 2);
            mDraggableRectangle.setY(mImagePane.getHeight() / 2 - mDraggableRectangle.getHeight() / 2);

            // Make sure that the mPhotoView and mDraggableRectangle are visible.
            mPhotoView.toFront();
            mDraggableRectangle.toFrontPref();

        } else {

            // Update the action label.
            mActionLabel.setText("Photo Capture");

            // While no image is captured by the web cam, only display the web cam capture button.
            mAcceptButton.setVisible(false);
            mAcceptButton.setManaged(false);
            mCaptureButton.setVisible(true);
            mCaptureButton.setManaged(true);
            mFilterSignatureBox.setVisible(false);

            // Determine the size of the mDraggableRectangle.
            switch (client) {
                case CLIENT_CHAIRMAN_PHOTO :
                case CLIENT_RESIDENT_PHOTO :
                    // mDraggableRectangle is set to a square.
                    mDraggableRectangle.setWidth(216);
                    mDraggableRectangle.setHeight(216);
                    break;
                default :
                    // mDraggableRectangle is set to a rectangle.
                    mDraggableRectangle.setWidth(384);
                    mDraggableRectangle.setHeight(216);

                    // todo : set selected calls the callback function?
                    mFilterSignatureCheckbox.setSelected(false);
            }

            // Try to launch the web cam. This operation will fail if the web cam is being used by another software.
            try {
                // Get the default web cam.
                Webcam webcam = Webcam.getDefault();
                webcam.setViewSize(new Dimension(640, 480));

                // Initialize the web cam itself.
                mWebcamPanel = new WebcamPanel(webcam);
                mWebcamPanel.setMirrored(true);

                // In order to add the web cam panel to the root pane, the JPanel must first be converted to a SwingNode.
                mWebcamNode = new SwingNode();
                mWebcamNode.setContent(mWebcamPanel);

                // Add the web cam panel to the root pane.
                mImagePane.getChildren().add(mWebcamNode);
            } catch (Exception e) {
                e.printStackTrace();

                // If the webcam failed to open, then display a message.
                mRootPane.setDisable(true);
                JOptionPane.showConfirmDialog(null, "Another application is using the webcam.");
                mRootPane.setDisable(false);

                return;
            }
        }
    }

    public void setListener(OnPhotoshopListener listener) {
        mListener = listener;
    }

}