package net.wlodek.geopurge;

public class Exhibit {
    public final int major;
    public final int minor;
    public final String title;
    public final String description;
    public final String image;

    public Exhibit(int major, int minor, String title,
                   String description, String image) {
        this.major = major;
        this.minor = minor;
        this.title = title;
        this.description = description;
        this.image = image;
    }
}
