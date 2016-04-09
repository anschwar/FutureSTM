# FutureSTM
A functional implementation of the future and promise abstraction which uses STM to mange the underlying data.

This project uses sbt for the dependency management. The documentation can be found [here](http://www.scala-sbt.org/0.13.1/docs/).
To create a jar the can be included in your project just use sbt package.

Due this project was part of a thesis there are several implementations. To create a new Future the apply function of the
companion object is used.

```
val future = optimized.future.Future {
    /** some task that should be calculated in the background */
}
```

The API further supports all common operations like callbacks (onSuccess, onFailure), functions that will be applied
after the computation is done (followedBy, combine) and the possibility to add alternative calculation steps (orAlt).

This project also includes some slides that give a short overview and the corresponding thesis which is written in german.