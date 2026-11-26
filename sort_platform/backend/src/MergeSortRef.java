/**
 * 平台自带的基准算法（归并排序）。
 * 判题时先用它跑一遍同样的数据，得到基准耗时，选手成绩按"和基准的比值"折算，
 * 这样换一台机器不用重新调档位。
 */
public class MergeSortRef implements SortStrategy {

    @Override
    public void sort(int[] arr) {
        if (arr == null || arr.length < 2) {
            return;
        }
        mergeSort(arr, 0, arr.length - 1);
    }

    private void mergeSort(int[] a, int left, int right) {
        if (left >= right) {
            return;
        }
        int mid = (left + right) >>> 1;
        mergeSort(a, left, mid);
        mergeSort(a, mid + 1, right);
        merge(a, left, mid, right);
    }

    private void merge(int[] a, int left, int mid, int right) {
        int[] tmp = new int[right - left + 1];
        int i = left;
        int j = mid + 1;
        int k = 0;
        while (i <= mid && j <= right) {
            tmp[k++] = a[i] <= a[j] ? a[i++] : a[j++];
        }
        while (i <= mid) {
            tmp[k++] = a[i++];
        }
        while (j <= right) {
            tmp[k++] = a[j++];
        }
        System.arraycopy(tmp, 0, a, left, tmp.length);
    }
}
