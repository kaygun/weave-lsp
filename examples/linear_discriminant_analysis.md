# Linear Discriminant Analysis in R

Adopted from Atabey Kaygun's blog post: [Linear Discriminant Analysis in R](https://kaygun.github.io/clean/2013-08-10-linear_discriminant_analysis_in_r.html).

## Description of the Problem

Linear Discriminant Analysis (LDA) is a generalization of Fisher's linear discriminant, a method used in statistics, pattern recognition, and machine learning to find a linear combination of features that characterizes or separates two or more classes of objects or events. Today, I'm going to demonstrate an implementation and classification analysis using R.

## Implementation in R

We construct a multi-class dataset and compute the linear discriminant projection and classification table using `MASS::lda`:

```name:r_lda_analysis lang:r code:visible output:visible
library(MASS)

# Set reproducible seed
set.seed(42)

# Generate synthetic dataset with 3 classes
group_a <- matrix(rnorm(60, mean = 1), ncol = 2)
group_b <- matrix(rnorm(60, mean = 5), ncol = 2)
group_c <- matrix(rnorm(60, mean = 9), ncol = 2)

df <- data.frame(
  x = c(group_a[,1], group_b[,1], group_c[,1]),
  y = c(group_a[,2], group_b[,2], group_c[,2]),
  group = factor(rep(c("A", "B", "C"), each = 30))
)

# Fit Linear Discriminant Analysis model
model <- lda(group ~ x + y, data = df)
cat("=== Linear Discriminants Scaling Matrix ===\n")
print(model$scaling)

# Compute classification predictions
pred <- predict(model, df)
cat("\n=== Confusion Matrix (Actual vs Predicted) ===\n")
print(table(Actual = df$group, Predicted = pred$class))
```
