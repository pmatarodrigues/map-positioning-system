package ipvc.estg.projeto4.Classes;

import android.graphics.Bitmap;

import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;

import java.util.LinkedList;

public class BuildingPicture {

    String filename;
    Bitmap bitmap;
    Mat image;
    Mat descriptors;
    MatOfKeyPoint keypoint;

    FeatureDetector detector;
    DescriptorExtractor descriptor;
    DescriptorMatcher matcher;

    LinkedList<DMatch> good_matches = new LinkedList<DMatch>();

    public BuildingPicture(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public Mat getImage() {
        return image;
    }

    public void setImage(Mat image) {
        this.image = image;
    }

    public Mat getDescriptors() {
        return descriptors;
    }

    public void setDescriptors(Mat descriptors) {
        this.descriptors = descriptors;
    }

    public MatOfKeyPoint getKeypoint() {
        return keypoint;
    }

    public void setKeypoint(MatOfKeyPoint keypoint) {
        this.keypoint = keypoint;
    }

    public FeatureDetector getDetector() {
        return detector;
    }

    public void setDetector(FeatureDetector detector) {
        this.detector = detector;
    }

    public DescriptorExtractor getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(DescriptorExtractor descriptor) {
        this.descriptor = descriptor;
    }

    public DescriptorMatcher getMatcher() {
        return matcher;
    }

    public void setMatcher(DescriptorMatcher matcher) {
        this.matcher = matcher;
    }

    public LinkedList<DMatch> getGood_matches() {
        return good_matches;
    }

    public void setGood_matches(LinkedList<DMatch> good_matches) {
        this.good_matches = good_matches;
    }
}
