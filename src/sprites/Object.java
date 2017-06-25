package sprites;

import managers.ImageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * This is static level object like a wall, ground or tree.
 *
 * Created by Aunmag on 2016.09.28.
 */

public class Object extends Sprite {

    public static List<Object> allGround = new ArrayList<>();
    public static List<Object> allDecoration = new ArrayList<>();
    public static List<Object> allAir = new ArrayList<>();

    public Object(float x, float y, ImageManager image) {
        super(x, y, 0, image);
    }

    public void delete() {}

}
