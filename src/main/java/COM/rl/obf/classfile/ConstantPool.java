/* ===========================================================================
 * $RCSfile: ConstantPool.java,v $
 * ===========================================================================
 *
 * RetroGuard -- an obfuscation package for Java classfiles.
 *
 * Copyright (c) 1998-2006 Mark Welsh (markw@retrologic.com)
 *
 * This program can be redistributed and/or modified under the terms of the
 * Version 2 of the GNU General Public License as published by the Free
 * Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 */

package COM.rl.obf.classfile;

import java.io.*;
import java.util.*;

import COM.rl.util.*;

/**
 * A representation of the data in a Java class-file's Constant Pool.
 * Constant Pool entries are managed by reference counting.
 * 
 * @author Mark Welsh
 */
public class ConstantPool
{
    // Constants -------------------------------------------------------------


    // Fields ----------------------------------------------------------------
    private ClassFile myClassFile;
    private List pool;


    // Class Methods ---------------------------------------------------------


    // Instance Methods ------------------------------------------------------
    /** Ctor, which initializes Constant Pool using an array of CpInfo. */
    public ConstantPool(ClassFile classFile, CpInfo[] cpInfo) throws Exception
    {
        this.myClassFile = classFile;
        this.pool = new ArrayList(Arrays.asList(cpInfo));
    }

    /** Return an Iterator of all Constant Pool entries. */
    public Iterator iterator()
    {
        return this.pool.iterator();
    }

    /** Return the Constant Pool length. */
    public int length()
    {
        return this.pool.size();
    }

    /** Return the specified Constant Pool entry. */
    public CpInfo getCpEntry(int i) throws Exception
    {
        if (i < this.pool.size())
        {
            return (CpInfo)this.pool.get(i);
        }
        throw new Exception("Constant Pool index out of range.");
    }

    /** Set the reference count for each element, using references from the owning ClassFile. */
    public void updateRefCount() throws Exception
    {
        // Reset all reference counts to zero
        this.walkPool(new PoolAction()
        {
            @Override
            public void defaultAction(CpInfo cpInfo) throws Exception
            {
                cpInfo.resetRefCount();
            }
        });

        // Count the direct references to Utf8 entries
        this.myClassFile.markUtf8Refs(this);

        // Count the direct references to NameAndType entries
        this.myClassFile.markNTRefs(this);

        // Go through pool, clearing the Utf8 entries which have no references
        this.walkPool(new PoolAction()
        {
            @Override
            public void utf8Action(Utf8CpInfo cpInfo) throws Exception
            {
                if (cpInfo.getRefCount() == 0)
                {
                    cpInfo.clearString();
                }
            }
        });
    }

    /** Increment the reference count for the specified element. */
    public void incRefCount(int i) throws Exception
    {
        CpInfo cpInfo = (CpInfo)this.pool.get(i);
        if (cpInfo == null)
        {
            // This can happen for JDK1.2 code so remove - 981123
//            throw new Exception("Illegal access to a Constant Pool element.");
        }
        else
        {
            cpInfo.incRefCount();
        }
    }

    /** Remap a specified Utf8 entry to the given value and return its new index. */
    public int remapUtf8To(String newString, int oldIndex) throws Exception
    {
        this.decRefCount(oldIndex);
        return this.addUtf8Entry(newString);
    }

    /** Decrement the reference count for the specified element, blanking if Utf and refs are zero. */
    public void decRefCount(int i) throws Exception
    {
        CpInfo cpInfo = (CpInfo)this.pool.get(i);
        if (cpInfo == null)
        {
            // This can happen for JDK1.2 code so remove - 981123
//            throw new Exception("Illegal access to a Constant Pool element.");
        }
        else
        {
            cpInfo.decRefCount();
        }
    }

    /** Add an entry to the constant pool and return its index. */
    public int addEntry(CpInfo entry) throws Exception
    {
        // Try to replace an old, blanked Utf8 entry
        int index = this.pool.size();
        this.pool.add(entry);
        return index;
    }

    /** Add a string to the constant pool and return its index. */
    protected int addUtf8Entry(String s) throws Exception
    {
        // Search pool for the string. If found, just increment the reference count and return the index
        for (int i = 0; i < this.pool.size(); i++)
        {
            Object o = this.pool.get(i);
            if (o instanceof Utf8CpInfo)
            {
                Utf8CpInfo entry = (Utf8CpInfo)o;
                if (entry.getString().equals(s))
                {
                    entry.incRefCount();
                    return i;
                }
            }
        }

        // No luck, so try to overwrite an old, blanked entry
        for (int i = 0; i < this.pool.size(); i++)
        {
            Object o = this.pool.get(i);
            if (o instanceof Utf8CpInfo)
            {
                Utf8CpInfo entry = (Utf8CpInfo)o;
                if (entry.getRefCount() == 0)
                {
                    entry.setString(s);
                    entry.incRefCount();
                    return i;
                }
            }
        }

        // Still no luck, so append a fresh Utf8CpInfo entry to the pool
        return this.addEntry(new Utf8CpInfo(s));
    }

    /** Data walker */
    class PoolAction
    {
        public void utf8Action(Utf8CpInfo cpInfo) throws Exception
        {
            this.defaultAction(cpInfo);
        }

        public void defaultAction(CpInfo cpInfo) throws Exception
        {
        }
    }

    private void walkPool(PoolAction pa) throws Exception
    {
        for (Iterator iter = this.pool.iterator(); iter.hasNext();)
        {
            Object o = iter.next();
            if (o instanceof Utf8CpInfo)
            {
                pa.utf8Action((Utf8CpInfo)o);
            }
            else if (o instanceof CpInfo)
            {
                pa.defaultAction((CpInfo)o);
            }
        }
    }
}
