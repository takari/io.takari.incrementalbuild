package test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import com.google.auto.value.AutoValue;

@AutoValue
abstract class Animal {
  static Animal create(Something name, int numberOfLegs) {
    // See "How do I...?" below for nested classes.
    return new AutoValue_Animal(name, numberOfLegs);
  }

  abstract Something name();
  abstract int numberOfLegs();
}
