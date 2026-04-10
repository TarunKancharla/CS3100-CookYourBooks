package app.cookyourbooks.gui;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.repository.RecipeRepository;

/** Inmemory repository with controls for async save testing. */
public final class InMemoryRecipeRepository implements RecipeRepository {
  final AtomicReference<Recipe> stored = new AtomicReference<>();
  final AtomicInteger saveCalls = new AtomicInteger();
  final CountDownLatch saveCompleted = new CountDownLatch(1);
  final CountDownLatch saveStarted = new CountDownLatch(1);
  final CountDownLatch releaseSave = new CountDownLatch(1);
  final AtomicReference<Thread> saveThread = new AtomicReference<>();
  volatile boolean blockOnSave = false;
  volatile boolean failOnSave = false;

  public InMemoryRecipeRepository(Recipe initial) {
    stored.set(initial);
  }

  @Override
  public void save(Recipe recipe) {
    saveCalls.incrementAndGet();
    saveThread.set(Thread.currentThread());
    saveStarted.countDown();
    try {
      if (blockOnSave) {
        releaseSave.await(2, TimeUnit.SECONDS);
      }
      if (failOnSave) {
        throw new RuntimeException("repository failure");
      }
      stored.set(recipe);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } finally {
      saveCompleted.countDown();
    }
  }

  @Override
  public Optional<Recipe> findById(String id) {
    Recipe current = stored.get();
    if (current == null) {
      return Optional.empty();
    }
    return current.getId().equals(id) ? Optional.of(current) : Optional.empty();
  }

  @Override
  public Optional<Recipe> findByTitle(String title) {
    Recipe current = stored.get();
    if (current == null) {
      return Optional.empty();
    }
    return current.getTitle().equalsIgnoreCase(title) ? Optional.of(current) : Optional.empty();
  }

  @Override
  public List<Recipe> findAllByTitle(String title) {
    return findByTitle(title).stream().toList();
  }

  @Override
  public List<Recipe> findAll() {
    Recipe current = stored.get();
    return current == null ? List.of() : List.of(current);
  }

  @Override
  public void delete(String id) {
    Recipe current = stored.get();
    if (current != null && current.getId().equals(id)) {
      stored.set(null);
    }
  }

  public void awaitSaveCompletion() throws InterruptedException {
    saveCompleted.await(2, TimeUnit.SECONDS);
  }
}
