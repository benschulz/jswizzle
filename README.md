# JSwizzle

A Java annotation processor that generates mixin interfaces. It is based on a [proof of concept](https://blogs.oracle.com/darcy/entry/properties_via_annotation_processing) by Joe Darcy.


## Generating Getters/Setters

```java
@Data
public class Point implements PointAccessors {
	final int x;
    final int y;
    
    public Point(int x, int y) {
    	this.x = x;
        this.y = y;
    }
}
```

Above code generates the following Mixin.

```java
public interface PointAccessors {
	public default int getX() {
    	return ((Point) this).x;
    }

	public default int getY() {
    	return ((Point) this).y;
    }
}
```

## Generating builder methods

```java
@Copyable
public class Point implements PointBuilders {
	final int x;
    final int y;
    
    public Point(int x, int y) {
    	this.x = x;
        this.y = y;
    }
}
```

Above code generates the following Mixin.

```java
public interface PointBuilders {
	public default Point withX(int newX) {
    	return new Point(
        	newX,
            ((Point) this).y
        );
    }

	public default Point withY(int newY) {
    	return new Point(
            ((Point) this).x,
            newY
        );
    }
}
```
