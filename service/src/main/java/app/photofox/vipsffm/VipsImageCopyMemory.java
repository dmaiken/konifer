package app.photofox.vipsffm;

import app.photofox.vipsffm.jextract.VipsRaw;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/**
 * Temporary binding for {@code vips_image_copy_memory()} until vips-ffm exposes it.
 */
public final class VipsImageCopyMemory {
    private static final String OPERATION_NAME = "vips_image_copy_memory";
    private static final Arena LIBRARY_ARENA = Arena.ofAuto();
    private static final MethodHandle COPY_MEMORY = copyMemoryHandle();

    private VipsImageCopyMemory() {
    }

    /**
     * Renders {@code source} to memory and returns a memory-backed image. Libvips may return
     * another reference to {@code source} when it is already backed by a simple memory buffer.
     *
     * <p>The returned image and its native memory are owned by {@code arena}.</p>
     */
    public static VImage copyMemory(Arena arena, VImage source) throws VipsError {
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(source, "source");

        var sourceAddress = source.getUnsafeStructAddress();
        if (!VipsValidation.isValidPointer(sourceAddress)) {
            VipsValidation.throwInvalidInputError(OPERATION_NAME, "image");
        }

        MemorySegment result;
        try {
            result = (MemorySegment) COPY_MEMORY.invokeExact(sourceAddress);
        } catch (Throwable cause) {
            throw new AssertionError("Unexpected failure calling " + OPERATION_NAME, cause);
        }

        if (!VipsValidation.isValidPointer(result)) {
            VipsValidation.throwInvalidOutputError(OPERATION_NAME, "result");
        }

        var arenaScopedResult = result.reinterpret(arena, VipsRaw::g_object_unref);
        return new VImage(arena, arenaScopedResult);
    }

    private static MethodHandle copyMemoryHandle() {
        var symbolLookup = VipsLibLookup.buildSymbolLoader(LIBRARY_ARENA)
            .or(SymbolLookup.loaderLookup())
            .or(Linker.nativeLinker().defaultLookup());
        var address = symbolLookup.find(OPERATION_NAME)
            .orElseThrow(() -> new UnsatisfiedLinkError("unresolved symbol: " + OPERATION_NAME));
        var descriptor = FunctionDescriptor.of(VipsRaw.C_POINTER, VipsRaw.C_POINTER);

        return Linker.nativeLinker().downcallHandle(address, descriptor);
    }
}
