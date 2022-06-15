import math
import numpy as np
import scipy
from scipy.optimize import least_squares

pi = math.pi

a = [0.098, 0.0425, 0.065]

U0 = 4.0 * pi * 1e-7

# 3 coil current values
coil_current = [3.0, 3.0, 3.0]
# coil_current = [2.7, 2.7, 2.7]
# coil_current = [4.13, 4.13, 4.13]

# 3 coil ordering calls
coil_order = [0, 2, 1]

# 3 coil parameters
# I0 = [412.0 * coil_current[0], 372.0 * coil_current[1], 412.0 * coil_current[2]]
I0 = [351.0 * coil_current[0], 372.0 * coil_current[1], 412.0 * coil_current[2]]

x1 = 0.0
y1 = 0.0
z1 = 0.0

alpha = 0.0
beta = 0.0
gamma = 0.0

R = None

def location_to_flux(x, c):
    px = x[0]
    py = x[1]
    pz = x[2]
    ax = x[3]
    ay = x[4]
    az = x[5]
    coil_id = c

    global alpha
    global beta
    global gamma

    global R

    xd = px - x1
    yd = py - y1
    zd = pz - z1

    coil_id = coil_id % len(coil_order)

    if coil_id == 1:
        xd = py - y1
        yd = pz - z1
        zd = px - x1
    elif coil_id == 2:
        xd = px - x1
        yd = pz - z1
        zd = py - y1

    sqt_xx1 = xd**2
    sqt_yy1 = yd**2
    sqt_zz1 = zd**2

    # Radical component is required for cylindrical coordinate system
    rc = (sqt_xx1 + sqt_yy1)**0.5

    sqt_rc = rc**2
    sqt_rca = (rc + a[coil_id])**2
    sqt_rca2 = (rc - a[coil_id])**2

    # This is a parameter for calculating the elliptical integrals
    m = (4.0 * a[coil_id] * rc) / (sqt_rca + sqt_zz1)

    tyr_const1 = pi / 2.0
    tyr_const2 = (pi / 8.0) * m
    tyr_const3 = (pi / 128.0) * m**2

    # K(k) elliptical function, this is a Taylor expansion of the K elliptical integral
    kofkc = tyr_const1 + tyr_const2 + 9.0 * tyr_const3

    # E(k) elliptical function, this is a Taylor expansion of the E elliptical integral
    eofkc = tyr_const1 - tyr_const2 - 3.0 * tyr_const3

    sqt_a = a[coil_id]**2
    rc_const = U0 * I0[coil_id] / (2.0 * pi)
    ra_const = sqt_rca2 + sqt_zz1

    # Radical component of B
    Brc = rc_const / rc * zd * (sqt_rca + sqt_zz1)**-0.5 * \
        (-kofkc + eofkc * (sqt_rc + sqt_a + sqt_zz1) / ra_const)

    # Axial component of B
    Bz = rc_const * (sqt_rca + sqt_zz1)**-0.5 * \
        (kofkc - eofkc * (sqt_rc - sqt_a + sqt_zz1) / ra_const)

    # This converts the polar component into Cartesian form
    Bx = Brc * xd / rc

    By = Brc * yd / rc

    if coil_id == 1:
        temp = Bx

        # Axial component of B
        Bx = Bz

        # This converts the polar component into Cartesian form
        Bz = By

        By = temp
    elif coil_id == 2:
        temp = By

        # Axial component of B
        By = Bz

        Bz = temp

    if math.isnan(Bx):
        Bx = 0.0
    if math.isnan(By):
        By = 0.0
    if math.isnan(Bz):
        Bz = 0.0

    # Define a 3-by-1 matrix
    flux_calculate = [ \
        # Row 0, Column 0
        Bx * 1e7, \
        # Row 1, Column 0
        By * 1e7, \
        # Row 2, Column 0
        Bz * 1e7]

    if ax is not alpha or ay is not beta or az is not gamma or R is None:
        alpha = ax
        beta = ay
        gamma = az

        # Define a 3-by-3 matrix
        R = [ \
            # Row 0, Column 0
            [math.cos(beta) * math.cos(gamma), \
            # Row 0, Column 1
            math.sin(alpha) * math.sin(beta) * math.cos(gamma) - \
                math.cos(alpha) * math.sin(gamma),
            # Row 0, Column 2
            math.cos(alpha) * math.sin(beta) * math.cos(gamma) + \
                math.sin(alpha) * math.sin(gamma)], \
            # Row 1, Column 0
            [math.cos(beta) * math.sin(gamma), \
            # Row 1, Column 1
            math.sin(alpha) * math.sin(beta) * math.sin(gamma) + \
                math.cos(alpha) * math.cos(gamma), \
            # Row 1, Column 2
            math.cos(alpha) * math.sin(beta) * math.sin(gamma) - \
                math.sin(alpha) * math.cos(gamma)], \
            # Row 2, Column 0
            [-math.sin(beta), \
            # Row 2, Column 1
            math.sin(alpha) * math.cos(beta), \
            # Row 2, Column 2
            math.cos(alpha) * math.cos(beta)]]

    # Calculate magnetic field by multiplication of two matrices
    B = [ \
        R[0][0] * flux_calculate[0] + R[0][1] * flux_calculate[1] + \
            R[0][2] * flux_calculate[2], \
        R[1][0] * flux_calculate[0] + R[1][1] * flux_calculate[1] + \
            R[1][2] * flux_calculate[2], \
        R[2][0] * flux_calculate[0] + R[2][1] * flux_calculate[1] + \
            R[2][2] * flux_calculate[2]]

    return B[0], B[1], B[2]

def objective_func(x, bx0, by0, bz0, bx1, by1, bz1, bx2, by2, bz2):
    Bx0, By0, Bz0 = location_to_flux(x, coil_order[0])
    Bx1, By1, Bz1 = location_to_flux(x, coil_order[1])
    Bx2, By2, Bz2 = location_to_flux(x, coil_order[2])

    return [Bx0 - bx0, By0 - by0, Bz0 - bz0, \
        Bx1 - bx1, By1 - by1, Bz1 - bz1, \
        Bx2 - bx2, By2 - by2, Bz2 - bz2]

def get_location_data(bx0, by0, bz0, bx1, by1, bz1, bx2, by2, bz2):
    bounds = [[-0.5, -0.5, -0.5, -pi, -pi, -pi], [0.5, 0.5, 0.5, pi, pi, pi]]
    result = least_squares(objective_func,
                           # Initial values of guess
                           # x0 = [0.0, 0.0, 0.5, 0.0, 0.0, 0.0],
                           x0 = [0.1, 0.1, 0.1, pi / 10.0, pi / 10.0, pi /10.0],
                           # x0 = [0.0, 0.0, 0.0, pi / 10.0, pi / 10.0, pi /10.0],
                           # Use Levenberg-Marquardt algorithm
                           # method = 'lm', \
                           # Use Trust Region Reflective algorithm
                           method = 'trf', \
                           # Enable debug print
                           # verbose = 1, \
                           # Levenberg-Marquardt algorithm does not support boundary check
                           bounds = bounds, \
                           # Parameters passed to the objective function
                           args = (bx0, by0, bz0, \
                                   bx1, by1, bz1, \
                                   bx2, by2, bz2))

    # print(result)

    return result.x

def get_magnetic_data(px, py, pz, ax, ay, az, coil_id):
    return location_to_flux([px, py, pz, ax, ay, az], coil_id)

def get_coil_id(i):
    return coil_order[i % len(coil_order)]

def run_test(dataset):
    for i in range(len(dataset)):
        bx0 = dataset[i][0]
        by0 = dataset[i][1]
        bz0 = dataset[i][2]
        bx1 = dataset[i][3]
        by1 = dataset[i][4]
        bz1 = dataset[i][5]
        bx2 = dataset[i][6]
        by2 = dataset[i][7]
        bz2 = dataset[i][8]
        result = get_location_data(bx0, by0, bz0, bx1, by1, bz1, bx2, by2, bz2)
        print(i, ': input:', dataset[i], 'output:', result)

test_sample = [[-128.5, -339, 1268, 92, 498, 204.5, -224.5, 57, 17], \
               [-122, -333.5, 1257.5, 83.5, 505, 219.5, -237.5, 55, 5], \
               [-69, -171.5, 660, 55.5, 331, 132, -232.5, 58, 18], \
               [-127.5, -333, 1254.5, 49.5, 313.5, 122, -238.5, 51, 0.5], \
               [0.0, -281.94, 488.33, 0.0, -186.82, -107.86, -74.99, 0.0, 0.0], \
               [-45.9, -21.15, 538.05, 24.3, -215.4, -31.95, -102.0, -7.05, -11.4], \
               [8.1, 577.05, 48.9, 5.55, -36.5, -234.95, -103.65, 5.7, 2.7], \
               [0.0, 0.0, 2372.66, 0.0, -1131.11, 0.0, -393.84, 0.0, 0.0], \
               [-23.55, -172.2, 496.35, 13.05, -126.45, -88.2, -103.8, -4.95, -4.95], \
               [-48.45, -118.2, 1197.9, 39.45, 75.45, -137.4, -244.8, -0.45, -17.55]]

print('location algorithm startup')
print('coil order:', coil_order)
print('coil current:', coil_current)
print('coil radius:', a)
print('coil magnetomotive force:', I0)
print('scipy version: ' + scipy.__version__)

# Run test to get location result by a list of samples
# run_test(test_sample)