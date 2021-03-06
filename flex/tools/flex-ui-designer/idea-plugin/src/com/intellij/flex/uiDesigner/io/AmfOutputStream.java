package com.intellij.flex.uiDesigner.io;

import com.intellij.util.PairConsumer;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.Identifiable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class AmfOutputStream extends PrimitiveAmfOutputStream {
  private static final byte[] STRING_ALIAS = {3, 115};

  TransactionableStringIntHashMap stringTable;
  TransactionableStringIntHashMap traitsTable;

  public AmfOutputStream(AbstractByteArrayOutputStream out) {
    super(out);
  }

  public void resetAfterMessage() {
    if (stringTable != null) {
      stringTable.clear();
    }
    if (traitsTable != null) {
      traitsTable.clear();
    }
  }

  @Override
  public void reset() {
    resetAfterMessage();
    super.reset();
  }

  /**
   * Write array as fixed flash Vector.<int>
   *
   * @param array of int
   * @throws IOException if an I/O error occurs or if array length out of range (29-bit number)
   */
  public void write(int[] array) {
    write(Amf3Types.VECTOR_INT);
    writeUInt29((array.length << 1) | 1);
    write(true);
    for (int i : array) {
      writeInt(i);
    }
  }

  /**
   * Write array as fixed flash Vector.<String>
   */
  public void write(String[] array) {
    write(Amf3Types.VECTOR_OBJECT);
    writeUInt29((array.length << 1) | 1);
    write(true);
    write(STRING_ALIAS);
    for (String s : array) {
      write(Amf3Types.STRING);
      writeAmfUtf(s, true);
    }
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public void writeStringCollection(Collection<String> collection) {
    write(Amf3Types.VECTOR_OBJECT);
    writeUInt29((collection.size() << 1) | 1);
    write(true);
    write(STRING_ALIAS);
    for (String s : collection) {
      write(Amf3Types.STRING);
      writeAmfUtf(s, true);
    }
  }

  public <T> void write(Collection<T> collection, PairConsumer<T, AmfOutputStream> outConsumer) {
    writeUInt29(collection.size());
    for (T e : collection) {
      outConsumer.consume(e, this);
    }
  }

  public <T extends Identifiable> void write(@Nullable List<T> collection) {
    writeUInt29(collection == null ? 0 : collection.size());
    if (collection == null) {
      return;
    }

    for (Identifiable e : collection) {
      writeUInt29(e.getId());
    }
  }

  public void write(Collection<TIntArrayList> collection) {
    writeUInt29(collection.size());
    for (TIntArrayList list : collection) {
      writeUInt29(list.size());
      list.forEach(value -> {
        writeUInt29(value);
        return true;
      });
    }
  }

  public void write(Collection collection, Class elementType) {
    write(collection, elementType.getName(), false);
  }

  public void write(Collection collection, String elementTypeName, boolean homogeneous) {
    write(collection, elementTypeName, homogeneous, false);
  }

  public void write(Collection collection, String elementTypeName, boolean homogeneous, boolean emptyAsNull) {
    if (collection == null || (emptyAsNull && collection.isEmpty())) {
      write(Amf3Types.NULL);
    }
    else {
      writeVector(collection, true, elementTypeName, homogeneous);
    }
  }

  public void write(String s) {
    if (s == null) {
      write(Amf3Types.NULL);
    }
    else {
      write(Amf3Types.STRING);
      writeStringWithoutType(s);
    }
  }

  public void writeAmfByteArray(byte[] bytes) {
    writeUInt29(bytes.length);
    write(bytes);
  }

  private void writeVector(Collection collection, boolean fixed, String elementTypeName, boolean homogeneous) {
    write(Amf3Types.VECTOR_OBJECT);

    final int n = collection.size();
    writeUInt29((n << 1) | 1);
    write(fixed);

    if (elementTypeName.equals("java.lang.Object")) {
      writeUInt29(1);
    }
    else {
      writeStringWithoutType(elementTypeName);
    }

    if (n == 0) {
      return;
    }

    for (Object current : collection) {
      if (current == null) {
        write(Amf3Types.NULL);
      }
      else {
        write(Amf3Types.OBJECT);
        writeObjectTraits(homogeneous ? elementTypeName : current.getClass().getName());
        ((AmfOutputable)current).writeExternal(this);
      }
    }
  }

  void writeStringWithoutType(String s) {
    if (s.isEmpty()) {
      write(1);
    }
    else if (!byReference(s)) {
      writeAmfUtf(s, true);
    }
  }

  private boolean byReference(String s) {
    if (stringTable == null) {
      stringTable = new TransactionableStringIntHashMap(32);
    }
    else {
      int reference = stringTable.get(s);
      if (reference != -1) {
        writeUInt29(reference << 1);
        return true;
      }
    }

    stringTable.put(s, stringTable.size());
    return false;
  }

  public void writeObjectTraits(String className) {
    if (!traitsByReference(className)) {
      write(7);
      writeStringWithoutType(className);
    }
  }

  private boolean traitsByReference(String s) {
    if (traitsTable == null) {
      traitsTable = new TransactionableStringIntHashMap(8);
    }
    else {
      int reference = traitsTable.get(s);
      if (reference != -1) {
        writeUInt29((reference << 2) | 1);
        return true;
      }
    }

    traitsTable.put(s, traitsTable.size());
    return false;
  }
}
