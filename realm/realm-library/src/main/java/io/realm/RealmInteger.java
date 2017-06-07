/*
 * Copyright 2017 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm;

import io.realm.annotations.Beta;
import io.realm.internal.ManagableObject;
import io.realm.internal.Row;


/**
 * A RealmInteger is a mutable, {@link java.lang.Long}-like numeric quantity.
 * RealmInteger implementations are not thread safe.  Like all Realm objects,
 * managed RealmIntegers may not be moved across threads.  Unmanaged RealmObject
 * may be moved across threads but require safe publication.
 * <p>
 * It wraps an internal CRDT counter:
 *
 * @see <a href="https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type"></a>
 * <p>
 * TODO: More complete docs, including examples of use.
 */
@Beta
public abstract class RealmInteger extends Number implements Comparable<RealmInteger>, ManagableObject {

    /**
     * Unmanaged Implementation.
     */
    private static final class UnmanagedRealmInteger extends RealmInteger {
        private long value;

        UnmanagedRealmInteger(long value) {
            this.value = value;
        }

        @Override
        public void set(long newValue) {
            value = newValue;
        }

        @Override
        public void increment(long inc) {
            value += inc;
        }

        @Override
        public void decrement(long dec) {
            value -= dec;
        }

        @Override
        public boolean isManaged() {
            return false;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public long longValue() {
            return value;
        }
    }


    /**
     * Managed Implementation.
     */
    static final class ManagedRealmInteger extends RealmInteger {
        private final ProxyState<?> proxyState;
        private final BaseRealm realm;
        private final Row row;
        private final long columnIndex;

        ManagedRealmInteger(ProxyState<? extends RealmObject> proxyState, long columnIndex) {
            this.proxyState = proxyState;
            this.realm = proxyState.getRealm$realm();
            this.row = proxyState.getRow$realm();
            this.columnIndex = columnIndex;
        }

        @Override
        public boolean isManaged() {
            return true;
        }

        @Override
        public boolean isValid() {
            return !realm.isClosed() && row.isAttached();
        }

        @Override
        public long longValue() {
            realm.checkIfValid();
            // Need to check isValid?
            return row.getLong(columnIndex);
        }

        @Override
        public void set(long value) {
            if (proxyState.isUnderConstruction()) {
                if (!proxyState.getAcceptDefaultValue$realm()) {  // Wat?
                    return;
                }

                row.getTable().setLong(columnIndex, row.getIndex(), value, true);
                return;
            }

            realm.checkIfValidAndInTransaction();
            row.setLong(columnIndex, value);
        }

        @Override
        public void increment(long inc) {
            realm.checkIfValidAndInTransaction();
            set(longValue() + inc);  // FIXME: wire up to native increment method
        }

        @Override
        public void decrement(long dec) {
            realm.checkIfValidAndInTransaction();
            set(longValue() - dec);  // FIXME: wire up to native increment method
        }
    }

    /**
     * Creates a new, unmanaged {@code RealmInteger} with the specified initial value.
     *
     * @param value initial value.
     */
    public static RealmInteger valueOf(long value) {
        return new UnmanagedRealmInteger(value);
    }

    /**
     * Creates a new, unmmanaged {@code RealmInteger} with the specified initial value.
     *
     * @param value initial value: parsed by {@code Long.parseLong}.
     */
    public static RealmInteger valueOf(String value) {
        return new UnmanagedRealmInteger(Long.parseLong(value));
    }

    /**
     * Creates a new, managed {@code RealmInteger}.
     *
     * @param proxyState Proxy state object.  Contains refs to Realm and Row.
     * @param columnIndex The index of the column that contains the RealmInteger.
     * @return a managed RealmInteger.
     */
    static RealmInteger managedRealmInteger(ProxyState<? extends RealmObject> proxyState, long columnIndex) {
        return new ManagedRealmInteger(proxyState, columnIndex);
    }

    /**
     * Seal the class.
     * In fact, this allows subclasses inside the package "realm.io".
     * Because it eliminates the synthetic constructor, though, we can live with that.
     * Don't make subclasses.
     */
    RealmInteger() {}

    /**
     * Sets the {@code RealmInteger} value.
     * Calling set() forcibly sets the RealmInteger to the provided value. Doing this means that
     * {@link #increment} and {@link #decrement} changes from other devices might be overridden.
     *
     * @param newValue new value.
     */
    public abstract void set(long newValue);

    /**
     * Increments the {@code RealmInteger}, adding the value of the argument.
     * Increment/decrement from all devices are reflected in the new value, which is guaranteed to converge.
     *
     * @param inc quantity to be added to the counter.
     */
    public abstract void increment(long inc);

    /**
     * Decrements the {@code RealmInteger}, subtracting the value of the argument.
     * Increment/decrement from all devices are reflected in the new value, which is guaranteed to converge.
     *
     * @param dec quantity to be subtracted from the counter.
     */
    public abstract void decrement(long dec);

    /**
     * {@inheritDoc}
     */
    @Override
    public final int intValue() {
        return (int) longValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final double doubleValue() {
        return (double) longValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final float floatValue() {
        return (float) longValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.valueOf(longValue());
    }

    /**
     * RealmIntegers compare strictly by their values.
     *
     * @param o the compare target
     * @return -1, 0, or 1, depending on whether this object's value is &gt;, =, or &lt; the target's.
     */
    @Override
    public final int compareTo(RealmInteger o) {
        long otherValue = o.longValue();
        long thisValue = longValue();

        return (thisValue == otherValue) ? 0 : ((thisValue > otherValue) ? 1 : -1);
    }

    /**
     * A RealmInteger's hash code depends only on its value.
     *
     * @return true if the target has the same value.
     */
    @Override
    public final int hashCode() {
        long thisValue = longValue();
        return (int) (thisValue ^ (thisValue >>> 32));
    }

    /**
     * Two RealmIntegers are {@code .equals} if and only if their longValues are equal.
     *
     * @param o compare target
     * @return true if the target has the same value.
     */
    @Override
    public final boolean equals(Object o) {
        if (o == this) { return true; }
        if (!(o instanceof RealmInteger)) { return false; }
        return longValue() == ((RealmInteger) o).longValue();
    }
}
