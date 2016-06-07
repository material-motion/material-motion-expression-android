/*
 * Copyright (C) 2016 The Material Motion Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.material.motion.expression;

import android.support.annotation.Keep;
import android.support.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * A {@link Term} defines a working set of {@link Intention Intentions} that accomplish a single
 * logical action. A Term can define modifiers that mutate its working set of Intentions.
 * Like all Expressions, a Term is immutable.
 *
 * <p>
 * A {@link Language} creates a new Term instance, passing itself into the Term's constructor,
 * to continue the {@link Expression} chain.
 * <p>
 * <code>Language &larr;<sub>new</sub>&larr; Term</code>
 *
 * <p>
 * A Term can use the Language instance at {@link #and} to continue the Expression chain.
 * <p>
 * <code>Term &larr;<sub>and</sub>&larr; Language</code>
 *
 * <p>
 * To define a modifier for this Term, create a method that returns {@link #modify(Modifier)}.
 * The return value must be T to enable chaining. Call this method to replace the previous Term on
 * the Expression chain with a new modified Term.
 * <p>
 * <code>
 *   Language                                               &larr;<sub>new</sub>&larr;
 *   <span style='text-decoration:line-through'>Term</span> &larr;<sub>modify</sub>&larr;
 *   Term
 * </code>
 *
 * <p>
 * A Term's {@link #intentions()} are generated by combining the {@link #intentions()} of the
 * previous chained Language with its working set of Intentions.
 */
public abstract class Term<T extends Term<?, ?>, L extends Language<L>> extends Expression {

  /**
   * The next {@link Language} on the {@link Expression} chain.
   * Use this to continue to chain {@link Term Terms} onto the Expression.
   */
  public final L and;

  /**
   * The previous {@link Language} on the {@link Expression} chain.
   */
  private final L language;
  /**
   * The working set of {@link Intention Intentions} for this {@link Term}.
   * To get the full set of Intentions, including chained Intentions from the {@link Language},
   * call {@link #intentions()}.
   */
  private final Work work;

  /**
   * The initializing constructor.
   *
   * <p>
   * Subclasses should call this from their own initializing constructor.
   *
   * @param language The {@link Language} instance passed into your {@link Term} constructor.
   * @param initializer The {@link Initializer} for the given {@link Intention Intentions}.
   * @param intentions The working set of Intentions for this Term.
   */
  protected Term(
      L language, @Nullable final Initializer initializer, final Intention... intentions) {
    this.language = language;
    this.work =
        new Work() {
          @Override
          public Intention[] work() {
            if (initializer != null) {
              initializer.fullInitialize(intentions);
            }
            return intentions;
          }
        };
    this.and =
        language.chain(
            new Work() {
              @Override
              Intention[] work() {
                return intentions();
              }
            });
  }

  /**
   * The chaining constructor.
   *
   * <p>
   * Subclasses should call this from their own chaining constructor, which must be annotated with
   * {@link Keep} and have the same parameter types.
   *
   * @param language The {@link Language} instance passed into your {@link Term} constructor.
   * @param work The {@link Work} instance passed into your Term constructor.
   */
  @Keep
  protected Term(L language, Work work) {
    this.language = language;
    this.work = work;
    this.and =
        language.chain(
            new Work() {
              @Override
              Intention[] work() {
                return intentions();
              }
            });
  }

  /**
   * Modifies the working set of {@link Intention Intentions} for this {@link Term} with the given
   * {@link Modifier}.
   *
   * <p>
   * Subclasses should only call this from their modifiers.
   *
   * @param modifier The Modifier to be applied to all Intentions in the working set.
   * @return A Term instance to enable chaining. Should be returned from your modifier.
   */
  protected final T modify(final Modifier modifier) {
    return chain(
        new Work() {
          @Override
          public Intention[] work() {
            Intention[] intentions = work.work();
            modifier.modify(intentions);
            return intentions;
          }
        });
  }

  private T chain(Work work) {
    return newInstance(work);
  }

  @SuppressWarnings({"TryWithIdenticalCatches", "unchecked"}) // Cast to Class<T> and Class<L>
  private T newInstance(Work work) {
    try {
      Class<T> klass = (Class<T>) getClass();
      Class<L> languageKlass = (Class<L>) language.getClass();

      Constructor<T> constructor = klass.getDeclaredConstructor(languageKlass, Work.class);
      constructor.setAccessible(true);

      return constructor.newInstance(language, work);
    } catch (NoSuchMethodException e) {
      throw new BadImplementationException(this, BadImplementationException.MISSING_CONSTRUCTOR, e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e.getCause());
    }
  }

  @Override
  public final Intention[] intentions() {
    Intention[] intentions = language.intentions();
    return concat(intentions, work.work());
  }

  /**
   * Concatenates two arrays.
   * @param first The first array
   * @param second The second array
   * @param <T> The type of elements in A and B
   * @return An array concatenating A + B
   */
  private static <T> T[] concat(T[] first, T[] second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    int firstLen = first.length;
    if (firstLen == 0) {
      return second;
    }
    int secondLen = second.length;
    if (secondLen == 0) {
      return first;
    }

    T[] result = Arrays.copyOf(first, firstLen + secondLen);
    System.arraycopy(second, 0, result, firstLen, secondLen);
    return result;
  }
}
