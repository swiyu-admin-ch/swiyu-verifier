#!/bin/bash

# Trivy Security Scanner for Maven Project
# This script installs Trivy and scans the project for vulnerabilities

set -e

echo "=== Trivy Security Scanner ==="
echo ""

# Check if Trivy is already installed
if ! command -v trivy &> /dev/null; then
    echo "Trivy not found. Installing..."

    # Download and install Trivy
    TRIVY_VERSION="0.58.1"
    wget -q https://github.com/aquasecurity/trivy/releases/download/v${TRIVY_VERSION}/trivy_${TRIVY_VERSION}_Linux-64bit.tar.gz

    tar zxf trivy_${TRIVY_VERSION}_Linux-64bit.tar.gz
    sudo mv trivy /usr/local/bin/
    sudo chmod +x /usr/local/bin/trivy

    # Cleanup
    rm trivy_${TRIVY_VERSION}_Linux-64bit.tar.gz

    echo "✓ Trivy installed successfully"
else
    echo "✓ Trivy already installed"
fi

echo ""
echo "=== Scanning verifier-application for vulnerabilities ==="
echo ""

# Scan the Maven project for HIGH and CRITICAL vulnerabilities
trivy fs \
    --scanners vuln \
    --severity HIGH,CRITICAL \
    --format table \
    ../



trivy fs --severity HIGH,CRITICAL ../

echo ""
echo "=== Scan complete ==="
echo ""
echo "To generate a JSON report, run:"
echo "trivy fs --scanners vuln --severity HIGH,CRITICAL --format json --output trivy-report.json verifier-application/"
