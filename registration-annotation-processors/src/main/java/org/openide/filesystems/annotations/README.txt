
This copy of LayerGeneratingProcessor is here because it is nearly impossible
to get meaningful output out of Maven regarding *which* annotation in which file
caused an error (typically reading or writing a layer or properties file more than
once), so they need to be patched to get that information to try to figure out where
in our annotation processors the conflict is occurring.
