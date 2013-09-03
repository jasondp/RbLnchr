package net.minecraft.launcher.updater.download;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.updater.download.DownloadListener;
import net.minecraft.launcher.updater.download.Downloadable;
import net.minecraft.launcher.updater.download.ProgressContainer;

public class DownloadJob {

   private static final int MAX_ATTEMPTS_PER_FILE = 5;
   private static final int ASSUMED_AVERAGE_FILE_SIZE = 5242880;
   private final Queue<Downloadable> remainingFiles;
   private final List<Downloadable> allFiles;
   private final List<Downloadable> failures;
   private final List<Downloadable> successful;
   private final List<ProgressContainer> progressContainers;
   private final DownloadListener listener;
   private final String name;
   private final boolean ignoreFailures;
   private final AtomicInteger remainingThreads;
   private boolean started;


   public DownloadJob(String name, boolean ignoreFailures, DownloadListener listener, Collection<Downloadable> files) {
      this.remainingFiles = new ConcurrentLinkedQueue();
      this.allFiles = Collections.synchronizedList(new ArrayList());
      this.failures = Collections.synchronizedList(new ArrayList());
      this.successful = Collections.synchronizedList(new ArrayList());
      this.progressContainers = Collections.synchronizedList(new ArrayList());
      this.remainingThreads = new AtomicInteger();
      this.name = name;
      this.ignoreFailures = ignoreFailures;
      this.listener = listener;
      if(files != null) {
         this.addDownloadables(files);
      }

   }

   public DownloadJob(String name, boolean ignoreFailures, DownloadListener listener) {
      this(name, ignoreFailures, listener, (Collection)null);
   }



    public void addDownloadables(Collection<Downloadable> downloadables) {
      if(this.started) {
         throw new IllegalStateException("Cannot add to download job that has already started");
      } else {
         this.allFiles.addAll(downloadables);
         this.remainingFiles.addAll(downloadables);

         Downloadable downloadable;
         for(Iterator i$ = downloadables.iterator(); i$.hasNext(); downloadable.getMonitor().setJob(this)) {
            downloadable = (Downloadable)i$.next();
            this.progressContainers.add(downloadable.getMonitor());
            if(downloadable.getExpectedSize() == 0L) {
               downloadable.getMonitor().setTotal(5242880L);
            } else {
               downloadable.getMonitor().setTotal(downloadable.getExpectedSize());
            }
         }

      }
   }

   public void addDownloadables(Downloadable ... downloadables) {
      if(this.started) {
         throw new IllegalStateException("Cannot add to download job that has already started");
      } else {
         Downloadable[] arr$ = downloadables;
         int len$ = downloadables.length;

         for(int i$ = 0; i$ < len$; ++i$) {
            Downloadable downloadable = arr$[i$];
            this.allFiles.add(downloadable);
            this.remainingFiles.add(downloadable);
            this.progressContainers.add(downloadable.getMonitor());
            if(downloadable.getExpectedSize() == 0L) {
               downloadable.getMonitor().setTotal(5242880L);
            } else {
               downloadable.getMonitor().setTotal(downloadable.getExpectedSize());
            }

            downloadable.getMonitor().setJob(this);
         }

      }
   }

   public void startDownloading(ThreadPoolExecutor executorService) {
      if(this.started) {
         throw new IllegalStateException("Cannot start download job that has already started");
      } else {
         this.started = true;
         if(this.allFiles.isEmpty()) {
            Launcher.getInstance().println("Download job \'" + this.name + "\' skipped as there are no files to download");
            this.listener.onDownloadJobFinished(this);
         } else {
            int threads = executorService.getMaximumPoolSize();
            this.remainingThreads.set(threads);
            Launcher.getInstance().println("Download job \'" + this.name + "\' started (" + threads + " threads, " + this.allFiles.size() + " files)");

            for(int i = 0; i < threads; ++i) {
               executorService.submit(new Runnable() {
                  public void run() {
                     DownloadJob.this.popAndDownload();
                  }
               });
            }
         }

      }
   }

   private void popAndDownload() {
      Downloadable downloadable;
      while((downloadable = (Downloadable)this.remainingFiles.poll()) != null) {
         if(downloadable.getNumAttempts() > 5) {
            if(!this.ignoreFailures) {
               this.failures.add(downloadable);
            }

            Launcher.getInstance().println("Gave up trying to download " + downloadable.getUrl() + " for job \'" + this.name + "\'");
         } else {
            try {
               String t = downloadable.download();
               this.successful.add(downloadable);
               Launcher.getInstance().println("Finished downloading " + downloadable.getTarget() + " for job \'" + this.name + "\'" + ": " + t);
            } catch (Throwable var3) {
               Launcher.getInstance().println("Couldn\'t download " + downloadable.getUrl() + " for job \'" + this.name + "\'", var3);
               this.remainingFiles.add(downloadable);
            }
         }
      }

      if(this.remainingThreads.decrementAndGet() <= 0) {
         this.listener.onDownloadJobFinished(this);
      }

   }

   public boolean shouldIgnoreFailures() {
      return this.ignoreFailures;
   }

   public boolean isStarted() {
      return this.started;
   }

   public boolean isComplete() {
      return this.started && this.remainingFiles.isEmpty() && this.remainingThreads.get() == 0;
   }

   public int getFailures() {
      return this.failures.size();
   }

   public int getSuccessful() {
      return this.successful.size();
   }

   public String getName() {
      return this.name;
   }

   public void updateProgress() {
      this.listener.onDownloadJobProgressChanged(this);
   }

   public float getProgress() {
      float current = 0.0F;
      float total = 0.0F;
      List result = this.progressContainers;
      synchronized(this.progressContainers) {
         Iterator i$ = this.progressContainers.iterator();

         while(true) {
            if(!i$.hasNext()) {
               break;
            }

            ProgressContainer progress = (ProgressContainer)i$.next();
            total += (float)progress.getTotal();
            current += (float)progress.getCurrent();
         }
      }

      float result1 = -1.0F;
      if(total > 0.0F) {
         result1 = current / total;
      }

      return result1;
   }
}
