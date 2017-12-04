/*
 * Copyright 2017, OpenCensus Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opencensus.example.gameoflife;

import static io.opencensus.example.gameoflife.GameOfLifeApplication.CALLER;
import static io.opencensus.example.gameoflife.GameOfLifeApplication.CLIENT_TAG_KEY;
import static io.opencensus.example.gameoflife.GameOfLifeApplication.METHOD;
import static io.opencensus.example.gameoflife.GameOfLifeApplication.ORIGINATOR;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.opencensus.common.Duration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.stats.Aggregation.Distribution;
import io.opencensus.stats.BucketBoundaries;
import io.opencensus.stats.Measure.MeasureDouble;
import io.opencensus.stats.Stats;
import io.opencensus.stats.StatsRecorder;
import io.opencensus.stats.View;
import io.opencensus.stats.View.AggregationWindow.Cumulative;
import io.opencensus.stats.View.Name;
import io.opencensus.stats.ViewManager;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

final class GameOfLifeClient {

  private static final Logger logger = Logger.getLogger(GameOfLifeClient.class.getName());
  private static final StatsRecorder statsRecorder = Stats.getStatsRecorder();
  private static final ViewManager viewManager = Stats.getViewManager();

  private static final List<Double> bucketBoundaries = Arrays.asList(0.0, 5.0, 10.0, 15.0, 20.0);
  private static final MeasureDouble CLIENT_MEASURE =
      MeasureDouble.create("gol_client_measure", "Sample measure for game of life client", "1");
  private static final Cumulative CUMULATIVE = Cumulative.create();
  private static final Name CLIENT_VIEW_NAME = Name.create("gol_client_view");
  private static final View CLIENT_VIEW =
      View.create(
          CLIENT_VIEW_NAME,
          "Sample view for game of life client",
          CLIENT_MEASURE,
          Distribution.create(BucketBoundaries.create(bucketBoundaries)),
          Arrays.asList(CLIENT_TAG_KEY, CALLER, METHOD, ORIGINATOR),
          CUMULATIVE);

  private final ManagedChannel channel;
  private final CommandProcessorGrpc.CommandProcessorBlockingStub blockingStub;

  // private static Server clientExporter;

  /** Construct client connecting to GameOfLife server at {@code host:port}. */
  GameOfLifeClient(String host, int port) {
    this(
        ManagedChannelBuilder.forAddress(host, port)
            // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
            // needing certificates.
            .usePlaintext(true));
  }

  /** Construct client for accessing RouteGuide server using the existing channel. */
  GameOfLifeClient(ManagedChannelBuilder<?> channelBuilder) {
    channel = channelBuilder.build();
    blockingStub = CommandProcessorGrpc.newBlockingStub(channel);
    logger.info("Client channel connected.");


    viewManager.registerView(CLIENT_VIEW);
  }

  void shutdown() throws InterruptedException {
    logger.info("Client channel shutting down...");
    channel.shutdownNow();
    GolUtils.printView(viewManager.getView(CLIENT_VIEW_NAME));
  }

  String executeCommand(String req) {
    CommandRequest request = CommandRequest.newBuilder().setReq(req).build();
    CommandResponse response;
    try {
      response = blockingStub.execute(request);
      // Record random stats against client tags.
      statsRecorder.newMeasureMap().put(CLIENT_MEASURE, new Random().nextInt(10)).record();
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      return null;
    }
    return response.getRetval();
  }

  public static void main(String[] args) throws Exception {
    try {
      StackdriverStatsExporter.createAndRegisterWithProjectId(
          "opencensus-java-stats-demo-app",
          Duration.create(5, 0));
    } catch (IOException e) {
      e.printStackTrace();
    }

    ClientzHandlers.startHttpServerAndRegisterAll(3002);
  }
}
