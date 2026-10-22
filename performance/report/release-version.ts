const releasePattern = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;

/** Accept only canonical vMAJOR.MINOR.PATCH subjects without prerelease suffixes or leading zeros. */
export function isStableRelease(subject: string): boolean {
    return releasePattern.test(subject);
}

/** Compares stable vMAJOR.MINOR.PATCH subjects in ascending release order. */
export function compareReleaseVersions(left: string, right: string): number {
    const leftVersion = releasePattern.exec(left);
    const rightVersion = releasePattern.exec(right);
    if (!leftVersion || !rightVersion) return left.localeCompare(right);

    for (let index = 1; index <= 3; index += 1) {
        const leftPart = Number(leftVersion[index]);
        const rightPart = Number(rightVersion[index]);
        if (leftPart < rightPart) return -1;
        if (leftPart > rightPart) return 1;
    }
    return 0;
}

/** Order records by release version, breaking same-version ties by completion time. */
export function compareReleaseRecords(
    left: { subject: string; completedAt: string },
    right: { subject: string; completedAt: string },
): number {
    return compareReleaseVersions(left.subject, right.subject)
        || left.completedAt.localeCompare(right.completedAt);
}
