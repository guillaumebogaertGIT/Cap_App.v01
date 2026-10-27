package be.cap.backend;

import java.io.IOException;

/** Domain stores can persist through a file or an enclosing database transaction. */
@FunctionalInterface
public interface StateWriter<T> { void write(T state) throws IOException; }
