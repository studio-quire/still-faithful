/* fficonfig.h for libffi 3.8.0 (LWJGL-CI/libffi), Windows x64 with MSVC.

   LWJGL's CI makes this with autogen.sh + configure under Cygwin, CC=msvcc.sh -m64, --host=x86_64-pc-cygwin.
   natives/build.py compiles libffi with cl and ml64 directly instead, so this file stands in for configure's
   output: the values configure arrives at for that host and compiler (cl is not GCC, so none of the GCC-only
   checks define anything). Only the macros the x64 sources read matter; the rest are kept so the file reads
   like the generated one. Checked by comparing our objects' disassembly with LWJGL's libffi.lib (docs/natives.md).
   Claude Opus 5.5, 2026-11-01. */

#define HAVE_INTTYPES_H 1
#define HAVE_MEMCPY 1
#define HAVE_STDINT_H 1
#define HAVE_STDIO_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_WCHAR_H 1

#define LT_OBJDIR ".libs/"
#define PACKAGE "libffi"
#define PACKAGE_BUGREPORT "http://github.com/libffi/libffi/issues"
#define PACKAGE_NAME "libffi"
#define PACKAGE_STRING "libffi 3.8.0"
#define PACKAGE_TARNAME "libffi"
#define PACKAGE_URL ""
#define PACKAGE_VERSION "3.8.0"
#define VERSION "3.8.0"

#define SIZEOF_DOUBLE 8
#define SIZEOF_LONG_DOUBLE 8
#define SIZEOF_SIZE_T 8
#define STDC_HEADERS 1

/* AH_BOTTOM in configure.ac, without HAVE_HIDDEN_VISIBILITY_ATTRIBUTE (GCC only). */
#ifdef LIBFFI_ASM
#define FFI_HIDDEN(name)
#else
#define FFI_HIDDEN
#endif
