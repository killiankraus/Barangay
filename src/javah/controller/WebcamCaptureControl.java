package javah.controller;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamUtils;
import com.github.sarxos.webcam.util.ImageUtils;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javah.util.DraggableSquare;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * This class will handle the web camera photo capturing.
 */
public class WebcamCaptureControl {

    public interface OnWebcamCaptureListener {
        void onAcceptButtonClicked(WritableImage croppedImage);
        void onCancelButtonClicked();
    }

    /**
     * The image view that will hold the captured photo.
     */
    @FXML private ImageView mCapturedPhotoView;

    /**
     * Visible only when mIsPhotoCaptured is true.
     */
    @FXML private ImageView mAcceptButton;

    /**
     * Serves as the main scene for the mWebcamPanel.
     */
    @FXML private Pane mWebcamPane;

    /**
     * The web cam stored and handled within mWebcamPanel.
     */
    private WebcamPanel mWebcamPanel;

    /**
     * Changes the state of the scene as it determines whether the webcam capturing occured.
     */
    private boolean mIsPhotoCaptured = false;

    /**
     * Temporary target file to store the captured photo.
     */
    private File mTempFile;

    /**
     * The file path of the temporary target file.
     */
    private String mTempFilePath;

    /**
     * The Image to be passed back to the MainControl to be stored as an asset.
     */
    private WritableImage mCapturedImage;

    /**
     * Used for cropping the image.
     */
    private DraggableSquare mDraggableSquare;

    private OnWebcamCaptureListener mListener;

    @FXML
    private void initialize() {
        // Initialize the temporary target file path and target file of the capture photo.
        mTempFilePath = System.getenv("PUBLIC") + "/Barangay131/Photos/temp.png";
        mTempFile = new File(mTempFilePath);

        // Initialize DraggableSquare object for image cropping.
        int sides = (int) mWebcamPane.getPrefWidth() / 2;
        mDraggableSquare = new DraggableSquare(
                (int) mWebcamPane.getPrefWidth() / 2 - sides / 2,
                (int) mWebcamPane.getPrefHeight() / 2 - sides / 2,
                sides,
                (int) mWebcamPane.getPrefWidth(),
                (int) mWebcamPane.getPrefHeight());

        // Add the DraggableSquare object to the mWebcamPane.
        mWebcamPane.getChildren().add(mDraggableSquare);

        resetScene();
    }

    @FXML
    /**
     * Crop the photo, then send it to the Main control and reset the scene and stop the webcam.
     * Hide the scene from the Main control after accomplishing this controller's task.
     * @param mouseEvent
     */
    public void onAcceptButtonClicked(MouseEvent mouseEvent) {
        WritableImage croppedImage = new WritableImage(
                mCapturedImage.getPixelReader(),
                (int) mDraggableSquare.getX(),
                (int) mDraggableSquare.getY(),
                (int) mDraggableSquare.getWidth(),
                (int) mDraggableSquare.getHeight());

        mCapturedPhotoView.setImage(croppedImage);

        mListener.onAcceptButtonClicked(croppedImage);
        setWebcamEnabled(false);
        resetScene();
    }
    @FXML
    /**
     * Capture photo from the web cam, then show the mAcceptButton.
     * If mCaptureButton is clicked while a photo has been captured, then the user is allowed to take another snap shot.
     * @param mouseEvent
     */
    public void onCaptureButtonClicked(MouseEvent mouseEvent) {
        // If capture photo button is clicked for the first time, then take a picture.
        // Else reset the scene to take another shot.
        if (!mIsPhotoCaptured) {
            mAcceptButton.setVisible(true);
            mAcceptButton.setManaged(true);

            // Capture photo and as a temporary file. Sadly, captured photo is mirrored.
            WebcamUtils.capture(mWebcamPanel.getWebcam(), mTempFile, ImageUtils.FORMAT_PNG);

            try {
                // Load the temporary file, that is, the image.
                BufferedImage capturedImage;
                capturedImage = ImageIO.read(new File(mTempFilePath));

                // Initialize mCapturedImage to load the flipped image.
                mCapturedImage = new WritableImage(capturedImage.getWidth(), capturedImage.getHeight());

                PixelWriter pixelWriter = mCapturedImage.getPixelWriter();

                int height = capturedImage.getHeight();
                int width = capturedImage.getWidth();

                // The flipping process...
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < width; x++)
                        pixelWriter.setArgb(width - 1 - x, y, capturedImage.getRGB(x, y));


                // Display the flipped captured photo.
                mCapturedPhotoView.setImage(mCapturedImage);
                mCapturedPhotoView.toFront();

                mDraggableSquare.setmVisible(true);
                mDraggableSquare.setmToFront();

            } catch (Exception e) {
                e.printStackTrace();
            }

            mIsPhotoCaptured = true;
        } else {
            resetScene();
        }
    }

    @FXML
    /**
     * Reset the scene and close the webcam, then close it.
     * @param actionEvent
     */
    public void onCancelButtonClicked(ActionEvent actionEvent) {
        setWebcamEnabled(false);
        resetScene();
        mListener.onCancelButtonClicked();
    }

    /**
     * Enable or disable the web cam.
     * @param enabled
     * @return false if enabling the web cam fails.
     */
    public boolean setWebcamEnabled(boolean enabled) {
        if (enabled) {
            // Try to launch the web cam. This operation will fail if the web cam is being used by another software.
            try {
                // Get the default web cam.
                Webcam webcam = Webcam.getDefault();
                webcam.setViewSize(new Dimension(640, 480));

                // Initialize the web cam itself.
                mWebcamPanel = new WebcamPanel(webcam);
                mWebcamPanel.setMirrored(true);

                // In order to add the web cam panel to the root pane, the JPanel must first be converted to a SwingNode.
                SwingNode swingNode = new SwingNode();
                swingNode.setContent(mWebcamPanel);

                // Add the web cam panel to the root pane.
                mWebcamPane.getChildren().add(swingNode);

            } catch (Exception e) {
                e.printStackTrace();

                return false;
            }

        } else
            // Stop the webcam.
            if (mWebcamPanel != null)
                mWebcamPanel.stop();

        return true;
    }

    public void setListener(OnWebcamCaptureListener listener) {
        mListener = listener;
    }

    /**
     * Resets the scene.
     */
    private void resetScene() {
        mAcceptButton.setVisible(false);
        mAcceptButton.setManaged(false);

        mDraggableSquare.setmVisible(false);

        // Set mCapturedPhotoView to the back to show the mWebcamPanel.
        mCapturedPhotoView.toBack();

        mIsPhotoCaptured = false;
    }
}