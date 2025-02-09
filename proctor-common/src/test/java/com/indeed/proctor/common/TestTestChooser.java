package com.indeed.proctor.common;

import com.google.common.collect.ImmutableList;
import com.indeed.proctor.common.model.Allocation;
import com.indeed.proctor.common.model.ConsumableTestDefinition;
import com.indeed.proctor.common.model.Range;
import com.indeed.proctor.common.model.TestBucket;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTestChooser {
    private static final List<TestBucket> TEST_BUCKETS = ImmutableList.of(
            new TestBucket("inactive", -1, "inactive", null),
            new TestBucket("control", 0, "control", null),
            new TestBucket("active", 1, "active", null)
    );
    private static final List<Allocation> ALLOCATIONS = ImmutableList.of(new Allocation(
            "${}",
            ImmutableList.of(
                    new Range(0, 0.5),
                    new Range(-1, 0),
                    new Range(1, 0.5)
            ),
            "#A1"
    ));
    private static final MaxBucketTestChooser TEST_CHOOSER = new MaxBucketTestChooser();

    private static final class MaxBucketTestChooser implements TestChooser<Void> {
        private final ConsumableTestDefinition consumableTestDefinition;

        public MaxBucketTestChooser() {
            final ConsumableTestDefinition consumableTestDefinition = new ConsumableTestDefinition();
            consumableTestDefinition.setBuckets(TEST_BUCKETS);
            consumableTestDefinition.setAllocations(ALLOCATIONS);
            this.consumableTestDefinition = consumableTestDefinition;
        }

        @Override
        public void printTestBuckets(@Nonnull final PrintWriter writer) {
        }

        @Nullable
        @Override
        public TestBucket getTestBucket(final int value) {
            return consumableTestDefinition.getBuckets()
                    .stream()
                    .filter(bucket -> bucket.getValue() == value)
                    .findFirst()
                    .orElse(null);
        }

        @Nonnull
        @Override
        public String[] getRules() {
            return consumableTestDefinition.getAllocations()
                    .stream()
                    .map(Allocation::getRule)
                    .toArray(String[]::new);
        }

        @Nonnull
        @Override
        public ConsumableTestDefinition getTestDefinition() {
            return consumableTestDefinition;
        }

        @Nonnull
        @Override
        public String getTestName() {
            return "example_tst";
        }

        @Nonnull
        @Override
        public Result chooseInternal(
                @Nullable final Void identifier,
                @Nonnull final Map<String, Object> values,
                @Nonnull final Map<String, TestBucket> testGroups
        ) {
            final Optional<Allocation> firstAllocation = consumableTestDefinition
                    .getAllocations()
                    .stream()
                    .findFirst();
            final Optional<Integer> maxBucketValue = firstAllocation
                    .map(Allocation::getRanges)
                    .map(Collection::stream)
                    .orElse(Stream.empty())
                    .map(Range::getBucketValue)
                    .max(Integer::compareTo);
            return new Result(
                    maxBucketValue.map(this::getTestBucket).orElse(null),
                    firstAllocation.orElse(null)
            );
        }
    }

    @Test
    public void testChoose_withEmptyForceGroupsOptions() {
        assertThat(compareTestChooserResults(
                choose(ForceGroupsOptions.empty()),
                new TestChooser.Result(
                        TEST_CHOOSER.getTestBucket(1),
                        ALLOCATIONS.get(0)
                )
        )).isTrue();
    }

    @Test
    public void testChoose_withForceGroup() {
        assertThat(compareTestChooserResults(
                choose(ForceGroupsOptions.builder()
                        .putForceGroup(TEST_CHOOSER.getTestName(), -1)
                        .build()),
                new TestChooser.Result(
                        TEST_CHOOSER.getTestBucket(-1),
                        null
                )
        )).isTrue();
    }

    @Test
    public void testChoose_withForceGroupToUnknown() {
        // Forcing to use an unknown bucket should be ignored.
        assertThat(compareTestChooserResults(
                choose(ForceGroupsOptions.builder()
                        .putForceGroup(TEST_CHOOSER.getTestName(), -2)
                        .build()),
                new TestChooser.Result(
                        TEST_CHOOSER.getTestBucket(1),
                        ALLOCATIONS.get(0)
                )
        )).isTrue();
    }

    @Test
    public void testChoose_withForceGroupForOtherTests() {
        assertThat(compareTestChooserResults(
                choose(ForceGroupsOptions.builder()
                        .putForceGroup("other_tst", 0)
                        .build()),
                new TestChooser.Result(
                        TEST_CHOOSER.getTestBucket(1),
                        ALLOCATIONS.get(0)
                )
        )).isTrue();
    }

    @Test
    public void testChoose_withDefaultToFallback() {
        assertThat(compareTestChooserResults(
                choose(ForceGroupsOptions.builder()
                        .setDefaultMode(ForceGroupsDefaultMode.FALLBACK)
                        .build()),
                new TestChooser.Result(
                        null,
                        null
                )
        )).isTrue();
    }

    @Test
    public void testChoose_withDefaultToMinActive() {
        assertThat(compareTestChooserResults(
                choose(ForceGroupsOptions.builder()
                        .setDefaultMode(ForceGroupsDefaultMode.MIN_LIVE)
                        .build()),
                new TestChooser.Result(
                        TEST_CHOOSER.getTestBucket(0),
                        null
                )
        )).isTrue();
    }

    @Test
    public void testChoose_withForceGroupAndDefaultToFallback() {
        assertThat(compareTestChooserResults(
                choose(ForceGroupsOptions.builder()
                        .setDefaultMode(ForceGroupsDefaultMode.FALLBACK)
                        .putForceGroup(TEST_CHOOSER.getTestName(), -1)
                        .build()),
                new TestChooser.Result(
                        TEST_CHOOSER.getTestBucket(-1),
                        null
                )
        )).isTrue();
    }

    @Test
    public void testChoose_withForceGroupToUnknownAndDefaultToFallback() {
        assertThat(compareTestChooserResults(
                choose(ForceGroupsOptions.builder()
                        .setDefaultMode(ForceGroupsDefaultMode.FALLBACK)
                        .putForceGroup(TEST_CHOOSER.getTestName(), -2)
                        .build()),
                new TestChooser.Result(
                        null,
                        null
                )
        )).isTrue();
    }

    @Test
    public void testChoose_withForceGroupAndDefaultToMinActive() {
        assertThat(compareTestChooserResults(
                choose(ForceGroupsOptions.builder()
                        .setDefaultMode(ForceGroupsDefaultMode.MIN_LIVE)
                        .putForceGroup(TEST_CHOOSER.getTestName(), -1)
                        .build()),
                new TestChooser.Result(
                        TEST_CHOOSER.getTestBucket(-1),
                        null
                )
        )).isTrue();
    }

    @Test
    public void testChoose_withForceGroupToUnknownAndDefaultToMinActive() {
        assertThat(compareTestChooserResults(
                choose(ForceGroupsOptions.builder()
                        .setDefaultMode(ForceGroupsDefaultMode.MIN_LIVE)
                        .putForceGroup(TEST_CHOOSER.getTestName(), -2)
                        .build()),
                new TestChooser.Result(
                        TEST_CHOOSER.getTestBucket(0),
                        null
                )
        )).isTrue();
    }

    private static boolean compareTestChooserResults(final TestChooser.Result a, final TestChooser.Result b) {
        return a.getTestBucket() == b.getTestBucket() && a.getAllocation() == b.getAllocation();
    }

    private static TestChooser.Result choose(final ForceGroupsOptions forceGroupsOptions) {
        return TEST_CHOOSER.choose(
                null,
                Collections.emptyMap(),
                Collections.emptyMap(),
                forceGroupsOptions
        );
    }
}
