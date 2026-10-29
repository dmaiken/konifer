const stableReleasePattern = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;

const releasePattern = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-rc\.([1-9]\d*))?$/;

/** Accept canonical stable or numbered RC release subjects. */
export function isReleaseVersion(subject: string): boolean {
    return releasePattern.test(subject);
}

/** Accept only canonical vMAJOR.MINOR.PATCH subjects without prerelease suffixes or leading zeros. */
export function isStableRelease(subject: string): boolean {
    return stableReleasePattern.test(subject);
}

/** Compares stable and numbered RC vMAJOR.MINOR.PATCH subjects in ascending release order. */
export function compareReleaseVersions(left: string, right: string): number {
    const leftVersion = releasePattern.exec(left);
    const rightVersion = releasePattern.exec(right);
    if (!leftVersion || !rightVersion) return left.localeCompare(right);

    for (let index = 1; index <= 3; index += 1) {
        const leftPart = BigInt(leftVersion[index]);
        const rightPart = BigInt(rightVersion[index]);
        if (leftPart < rightPart) return -1;
        if (leftPart > rightPart) return 1;
    }
    const leftRc = leftVersion[4];
    const rightRc = rightVersion[4];
    if (leftRc === undefined) return rightRc === undefined ? 0 : 1;
    if (rightRc === undefined) return -1;
    const leftNumber = BigInt(leftRc);
    const rightNumber = BigInt(rightRc);
    return leftNumber < rightNumber ? -1 : leftNumber > rightNumber ? 1 : 0;
}

/** Order records by release version, breaking same-version ties by completion time. */
export function compareReleaseRecords(
    left: { subject: string; completedAt: string },
    right: { subject: string; completedAt: string },
): number {
    return compareReleaseVersions(left.subject, right.subject)
        || left.completedAt.localeCompare(right.completedAt);
}
